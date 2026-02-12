# Docker & Docker Compose — 전체 정리 가이드

## 이 가이드에서 다루는 것

- Docker의 핵심 개념 (이미지, 컨테이너, Dockerfile)
- **멀티 스테이지 빌드**로 최적화된 이미지 만들기
- **Docker Compose**로 다중 컨테이너 애플리케이션 구성
- 네트워크 격리, 볼륨, 환경변수 설정
- 실제 프로젝트 (ToDo 앱: Vue + Spring Boot + MariaDB)

---

## Part 1: Docker 핵심 개념

### 이미지와 컨테이너의 관계

```
Dockerfile ──(빌드)──→ 이미지(템플릿) ──(실행)──→ 컨테이너(인스턴스)
                         읽기 전용            읽기/쓰기
                         1개 이미지     →     N개 컨테이너 가능
```

### 자주 헷갈리는 부분

| 잘못된 이해 | 올바른 이해 |
|---|---|
| 파일을 이미지라고 부른다 | 이미지는 코드+런타임+라이브러리 등을 레이어로 패키징한 **읽기 전용 템플릿** |
| 컨테이너가 이미지를 감싼다 | 이미지를 **실행하면** 컨테이너가 생성된다 (1이미지 → N컨테이너 가능) |
| Docker Hub에 컨테이너를 저장한다 | Docker Hub에 저장되는 건 **이미지** (컨테이너는 실행 중인 인스턴스일 뿐) |
| 브릿지 연결 전엔 통신 불가 | 같은 호스트 내 컨테이너는 **기본 bridge 네트워크**로 통신 가능 |

---

## Part 2: Dockerfile (이미지 만들기)

### Spring Boot Dockerfile (멀티 스테이지 빌드)

```dockerfile
## 1단계: 빌드 스테이지
FROM gradle:jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew clean build -x test --no-daemon

## 2단계: 실행 스테이지
FROM amazoncorretto:21-alpine
COPY --from=build /app/build/libs/*.jar ./
RUN mv $(ls *.jar | grep -v plain) app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**줄 단위 해설:**

| 줄 | 명령어 | 설명 |
|---|---|---|
| `FROM gradle:jdk21-alpine AS build` | 베이스 이미지 | Gradle + JDK 21 이미지로 시작, `build`라는 이름 지정 |
| `WORKDIR /app` | 작업 디렉토리 | 이후 명령어가 실행될 경로 |
| `COPY . .` | 파일 복사 | 호스트의 모든 파일 → 컨테이너의 /app |
| `RUN ./gradlew clean build -x test --no-daemon` | 빌드 실행 | JAR 파일 생성 (-x test: 테스트 스킵) |
| `FROM amazoncorretto:21-alpine` | 실행용 이미지 | JRE만 포함된 가벼운 이미지 |
| `COPY --from=build ...` | 빌드 결과 복사 | 1단계에서 만든 JAR만 가져옴 |
| `RUN mv ... app.jar` | 파일명 변경 | plain JAR 제외하고 실행 가능한 JAR를 app.jar로 |
| `ENTRYPOINT` | 실행 명령 | 컨테이너 시작 시 `java -jar app.jar` 실행 |

**멀티 스테이지 빌드의 장점:**

```
싱글 스테이지: Gradle + JDK + 소스코드 + JAR = ~800MB 😱
멀티 스테이지: JRE + JAR만                    = ~150MB ✅
```

- 빌드 도구(Gradle)와 소스코드는 최종 이미지에 포함되지 않음
- 보안: 소스코드 노출 방지
- 효율: 이미지 크기 대폭 감소

---

### Vue.js Dockerfile (2가지 버전)

#### 버전 1: 개발용 (Docker Compose에서 사용)

```dockerfile
FROM node:lts-alpine
WORKDIR /app
COPY . .
RUN npm install
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
```

- Vite 개발 서버 실행 (핫 리로드 지원)
- `--host 0.0.0.0`: 컨테이너 외부에서 접근 가능하도록 설정
- 포트: 5173

#### 버전 2: 프로덕션용 (Kubernetes에서 사용)

```dockerfile
# 빌드 스테이지
FROM node:lts-alpine AS build-stage
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# 실행 스테이지
FROM nginx:stable-alpine AS production-stage
COPY --from=build-stage /app/dist /usr/share/nginx/html
COPY ./nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**두 버전의 차이:**

| 항목 | 개발용 | 프로덕션용 |
|---|---|---|
| 서버 | Vite 개발 서버 | Nginx |
| 포트 | 5173 | 80 |
| 핫 리로드 | O | X |
| 이미지 크기 | ~300MB | ~30MB |
| 용도 | Docker Compose 로컬 개발 | Kubernetes 배포 |

---

### nginx.conf (Vue SPA를 위한 설정)

```nginx
server {
    listen 80;
    server_name localhost;

    location / {
        root /usr/share/nginx/html/;
        try_files $uri $uri/ /index.html;
    }
}
```

| 설정 | 설명 |
|---|---|
| `listen 80` | 80번 포트에서 HTTP 수신 |
| `root /usr/share/nginx/html/` | 정적 파일 위치 |
| `try_files $uri $uri/ /index.html` | SPA 핵심! 파일 없으면 index.html로 폴백 |

**`try_files`가 중요한 이유:**
```
Vue Router에서 /about 페이지 접근 시:
1. /about 파일 찾기 → 없음
2. /about/ 디렉토리 찾기 → 없음
3. /index.html 반환 → Vue Router가 /about 처리 ✅

try_files 없으면?
1. /about 파일 찾기 → 없음
2. 404 Not Found! ❌
```

---

## Part 3: Docker Compose (다중 컨테이너 관리)

### docker-compose.yaml 전체 구조

```yaml
services:
  mariadb:          # DB 서비스
    image: mariadb:11
    container_name: mariadb
    ports:
      - "5506:3306"
    environment:
      MARIADB_ROOT_PASSWORD: root1234
      MARIADB_DATABASE: todolist
      MARIADB_USER: todouser
      MARIADB_PASSWORD: todopass
    volumes:
      - mariadb-data:/var/lib/mysql
    networks:
      - app-private

  backend:          # Spring Boot 서비스
    build: ./chap01-boot-project
    container_name: backend
    ports:
      - "8080:8080"
    networks:
      - app-public
      - app-private
    depends_on:
      - mariadb

  frontend:         # Vue 서비스
    build: ./chap01-vue-project
    container_name: frontend
    ports:
      - "5173:5173"
    networks:
      - app-public

  tester:           # 네트워크 테스트용
    image: alpine
    container_name: net-tester
    command: sleep 3600
    networks:
      - app-public

networks:
  app-public:
    driver: bridge
  app-private:
    driver: bridge
    internal: true    # 외부 인터넷 접근 차단!

volumes:
  mariadb-data:
```

---

### 네트워크 구조 (가장 중요!)

```
┌──── 인터넷 ────────────────────────────────────────────┐
│                                                        │
│   app-public (bridge)              app-private          │
│   외부 접근 가능                    (bridge, internal)   │
│                                    외부 접근 차단!       │
│   ┌──────────┐                                         │
│   │ frontend │─────── app-public ────┐                 │
│   │ :5173    │                       │                 │
│   └──────────┘                       │                 │
│                                      │                 │
│   ┌──────────┐                       │                 │
│   │ tester   │─────── app-public ────┤                 │
│   │ alpine   │                       │                 │
│   └──────────┘                       │                 │
│                                      │                 │
│                              ┌───────┴───────┐         │
│                              │   backend     │         │
│                              │   :8080       │         │
│                              │               │         │
│                              │ app-public  ──┤         │
│                              │ app-private ──┤         │
│                              └───────┬───────┘         │
│                                      │                 │
│                              ┌───────┴───────┐         │
│                              │   mariadb     │         │
│                              │   :3306       │         │
│                              │ app-private   │         │
│                              └───────────────┘         │
└────────────────────────────────────────────────────────┘
```

**핵심 포인트:**

| 서비스 | 네트워크 | 접근 가능 대상 |
|---|---|---|
| frontend | app-public | backend (O), mariadb (X) |
| backend | app-public + app-private | frontend (O), mariadb (O) |
| mariadb | app-private | backend (O), frontend (X) |
| tester | app-public | backend (O), mariadb (X) |

- `internal: true` → 해당 네트워크는 **외부 인터넷과 완전히 격리**
- MariaDB는 `app-private`에만 속하므로 외부에서 직접 접근 불가
- Backend만 양쪽 네트워크에 연결 → **프론트와 DB 사이의 다리 역할**

---

### 각 설정 상세 설명

#### MariaDB 서비스

```yaml
mariadb:
  image: mariadb:11          # Docker Hub의 MariaDB 11 공식 이미지
  container_name: mariadb    # 컨테이너 이름 = 네트워크에서의 호스트명
  ports:
    - "5506:3306"            # 호스트 5506 → 컨테이너 3306 (외부 DB 접속용)
  environment:
    MARIADB_ROOT_PASSWORD: root1234    # root 비밀번호
    MARIADB_DATABASE: todolist         # 자동 생성할 데이터베이스
    MARIADB_USER: todouser             # 자동 생성할 사용자
    MARIADB_PASSWORD: todopass         # 사용자 비밀번호
  volumes:
    - mariadb-data:/var/lib/mysql      # 영구 저장소 마운트
  networks:
    - app-private                      # 내부 네트워크만
```

**`container_name`의 역할:**
- Docker 내부 DNS에서 **호스트명**으로 사용
- Backend에서 `jdbc:mariadb://mariadb:3306/todolist`로 접근
  - 여기서 `mariadb`가 바로 `container_name`!

#### Backend 서비스

```yaml
backend:
  build: ./chap01-boot-project    # 이 경로의 Dockerfile로 빌드
  container_name: backend
  ports:
    - "8080:8080"
  networks:
    - app-public                  # 프론트엔드와 통신
    - app-private                 # DB와 통신
  depends_on:
    - mariadb                     # mariadb 먼저 시작
```

**`depends_on`의 의미:**
- `mariadb` 컨테이너가 **먼저 시작**된 후 backend 시작
- 주의: 컨테이너 "시작"이지 "준비 완료"가 아님!
  - DB가 완전히 초기화되기 전에 Backend가 연결을 시도할 수 있음
  - 실제 프로덕션에서는 retry 로직이나 `healthcheck` 조건 필요

#### Frontend 서비스

```yaml
frontend:
  build: ./chap01-vue-project
  container_name: frontend
  ports:
    - "5173:5173"
  networks:
    - app-public                  # backend와만 통신 가능
```

#### Tester 서비스

```yaml
tester:
  image: alpine
  container_name: net-tester
  command: sleep 3600             # 1시간 동안 대기 (테스트용)
  networks:
    - app-public
```

- 네트워크 연결 테스트를 위한 **유틸리티 컨테이너**
- `docker exec -it net-tester sh`로 접속하여 `ping`, `wget` 등 테스트

---

### 볼륨 (Volume)

```yaml
volumes:
  mariadb-data:     # Named Volume (이름 있는 볼륨)
```

```
컨테이너 삭제 → 데이터 유지!

docker-compose down       → 컨테이너 삭제, 볼륨 유지 ✅
docker-compose down -v    → 컨테이너 + 볼륨 모두 삭제 ⚠️
```

| 볼륨 종류 | 특징 | 예시 |
|---|---|---|
| **Named Volume** | Docker가 관리, 이름으로 참조 | `mariadb-data:/var/lib/mysql` |
| **Bind Mount** | 호스트 특정 경로와 직접 연결 | `./data:/var/lib/mysql` |
| **tmpfs** | 메모리에만 저장 (임시) | `tmpfs: /run` |

---

## Part 4: 애플리케이션 코드

### Backend (Spring Boot) — ToDo API

#### 프로젝트 구조
```
chap01-boot-project/
├── Dockerfile
├── build.gradle
└── src/main/java/com/ohgiraffers/bootproject/
    ├── Chap01BootProjectApplication.java    # 메인
    ├── config/
    │   └── WebConfig.java                   # CORS 설정
    ├── controller/
    │   ├── CalculatorController.java        # 덧셈 API
    │   └── TodoController.java              # ToDo CRUD API
    ├── dto/
    │   ├── CalculatorDto.java
    │   ├── TodoRequestDto.java
    │   └── TodoResponseDto.java
    ├── entity/
    │   └── Todo.java                        # DB 엔티티
    ├── repository/
    │   └── TodoRepository.java              # JPA 레포지토리
    └── service/
        ├── CalculatorService.java
        └── TodoService.java
```

#### API 엔드포인트

| Method | URL | 기능 | Request Body |
|---|---|---|---|
| GET | `/health` | 헬스 체크 | - |
| GET | `/plus?num1=10&num2=20` | 덧셈 | - |
| GET | `/api/todos` | 전체 ToDo 조회 | - |
| POST | `/api/todos` | ToDo 생성 | `{"title": "할일"}` |
| DELETE | `/api/todos/{id}` | ToDo 삭제 | - |
| PATCH | `/api/todos/{id}/toggle` | 완료 상태 토글 | - |

#### 주요 설정 (application.properties)

```properties
spring.application.name=chap01-boot-project

# DB 연결 (Docker 컨테이너명으로 접근!)
spring.datasource.url=jdbc:mariadb://mariadb:3306/todolist
spring.datasource.username=todouser
spring.datasource.password=todopass
spring.datasource.driver-class-name=org.mariadb.jdbc.Driver

# JPA
spring.jpa.hibernate.ddl-auto=update    # 테이블 자동 생성/수정
spring.jpa.show-sql=true                # SQL 로그 출력
spring.jpa.properties.hibernate.format_sql=true
```

> **핵심:** `mariadb:3306`에서 `mariadb`는 Docker Compose의 서비스명!
> Docker 내부 DNS가 서비스명을 IP로 자동 변환해줍니다.

#### CORS 설정 (WebConfig.java)

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
    }
}
```

**왜 CORS가 필요한가?**
```
Frontend: http://localhost:5173  ← Origin A
Backend:  http://localhost:8080  ← Origin B

브라우저 보안 정책: Origin이 다르면 요청 차단!
→ CORS 설정으로 "5173에서 오는 요청은 허용"이라고 선언
```

#### Todo 엔티티 (Todo.java)

```java
@Entity
@Table(name = "todos")
public class Todo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Boolean completed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist    // INSERT 전에 자동 실행
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate     // UPDATE 전에 자동 실행
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

**생성되는 테이블:**
```sql
CREATE TABLE todos (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
```

---

### Frontend (Vue 3) — ToDo UI

#### API 호출 모듈 (todoApi.js)

```javascript
import axios from 'axios';
const API_BASE_URL = 'http://localhost:8080/api/todos';

export const getTodos = async () => {
    const response = await axios.get(API_BASE_URL);
    return response.data;
};

export const createTodo = async (title) => {
    const response = await axios.post(API_BASE_URL, { title });
    return response.data;
};

export const deleteTodo = async (id) => {
    await axios.delete(`${API_BASE_URL}/${id}`);
};

export const toggleTodo = async (id) => {
    const response = await axios.patch(`${API_BASE_URL}/${id}/toggle`);
    return response.data;
};
```

#### App.vue — 주요 기능

```vue
<script setup>
import { ref, onMounted } from "vue";
import { getTodos, createTodo, deleteTodo, toggleTodo } from './api/todoApi';

const todos = ref([]);
const newTodoTitle = ref('');

// 컴포넌트 마운트 시 ToDo 목록 로드
onMounted(async () => {
    todos.value = await getTodos();
});

// ToDo 추가
const addTodo = async () => {
    if (newTodoTitle.value.trim()) {
        await createTodo(newTodoTitle.value);
        todos.value = await getTodos();
        newTodoTitle.value = '';
    }
};

// ToDo 삭제
const removeTodo = async (id) => {
    await deleteTodo(id);
    todos.value = await getTodos();
};

// 완료 상태 토글
const toggleTodoStatus = async (id) => {
    await toggleTodo(id);
    todos.value = await getTodos();
};
</script>
```

---

## Part 5: 실습 명령어

### Docker Compose 기본 명령어

```bash
# 빌드 + 실행 (포그라운드)
docker-compose up --build

# 빌드 + 실행 (백그라운드)
docker-compose up -d --build

# 로그 확인
docker-compose logs -f              # 전체
docker-compose logs -f backend      # 특정 서비스

# 상태 확인
docker-compose ps

# 종료 (컨테이너 삭제, 볼륨 유지)
docker-compose down

# 종료 + 볼륨 삭제 (DB 데이터 초기화!)
docker-compose down -v

# 특정 서비스만 재시작
docker-compose restart backend

# 컨테이너 내부 접속
docker-compose exec backend /bin/sh
docker-compose exec mariadb mariadb -u root -proot1234
```

### 네트워크 테스트

```bash
# tester 컨테이너에서 네트워크 연결 확인
docker exec -it net-tester sh

# app-public 네트워크 테스트 (성공해야 함)
ping backend       # ✅
ping frontend      # ✅

# app-private 네트워크 테스트 (실패해야 함)
ping mariadb       # ❌ (tester는 app-private에 없음)
```

### 접속 URL

| 서비스 | URL | 용도 |
|---|---|---|
| Frontend | http://localhost:5173 | Vue ToDo 앱 |
| Backend Health | http://localhost:8080/health | 헬스 체크 |
| Backend API | http://localhost:8080/api/todos | ToDo API |
| Calculator | http://localhost:8080/plus?num1=5&num2=3 | 덧셈 API |
| MariaDB | localhost:5506 | DB 클라이언트 접속 |

---

## Part 6: Docker → Kubernetes 전환 포인트

Docker Compose에서 Kubernetes로 넘어갈 때 달라지는 점:

| 항목 | Docker Compose | Kubernetes |
|---|---|---|
| **정의 파일** | docker-compose.yaml (1개) | Deployment, Service, Ingress 등 (여러 개) |
| **서비스 디스커버리** | 컨테이너명으로 접근 | Service 이름으로 접근 |
| **외부 접근** | ports 매핑 | NodePort 또는 Ingress |
| **네트워크 격리** | networks (manual) | Namespace + NetworkPolicy |
| **영구 저장소** | volumes | PV + PVC |
| **설정 관리** | environment | ConfigMap + Secret |
| **헬스 체크** | healthcheck | Liveness/Readiness Probe |
| **자가 치유** | restart: always | ReplicaSet (더 강력) |
| **스케일링** | `docker-compose scale` | `kubectl scale` / HPA |
| **무중단 배포** | 수동 관리 | RollingUpdate (자동) |

> Docker Compose는 **단일 머신에서의 다중 컨테이너 관리**,
> Kubernetes는 **여러 머신(클러스터)에서의 대규모 컨테이너 오케스트레이션**에 적합합니다.

---

## 전체 학습 흐름 (Docker → Kubernetes)

```
[Docker 기초]
  Dockerfile → 이미지 → 컨테이너 이해
      │
      ▼
[Docker Compose]
  여러 컨테이너를 하나로 관리 (Vue + Boot + MariaDB)
  네트워크 격리, 볼륨, 환경변수
      │
      ▼
[K8s Section 01] NodePort
  Kubernetes 기본 개념 (Pod, Deployment, Service)
      │
      ▼
[K8s Section 02] ClusterIP + Ingress
  프로덕션 아키텍처 (URL 라우팅, 내부 네트워크)
      │
      ▼
[K8s Section 03] Rolling Update
  무중단 배포 (버전 관리, 롤백)
      │
      ▼
[K8s Section 04] PV/PVC
  영구 저장소 (데이터 보존)
      │
      ▼
[K8s Section 05] ConfigMap & Secret
  설정 외부화 (환경변수 주입)
      │
      ▼
[K8s Section 06] Probes
  헬스 체크 (자동 감시, 자가 치유)
```
