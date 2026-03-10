# section02 전체 파일 역할

## 파일 목록

| 파일 | 종류 | 역할 |
|---|---|---|
| `vue002dep.yml` | Deployment | Vue 프론트엔드 Pod 생성 |
| `vue002ser.yml` | Service | Vue Pod에 접근할 내부 주소 부여 |
| `boot002dep.yml` | Deployment | Spring Boot 백엔드 Pod 생성 |
| `boot002ser.yml` | Service | Boot Pod에 접근할 내부 주소 부여 |
| `ingress002.yml` | Ingress | 외부 요청을 URL 경로별로 분배 |

---

## 전체 구조도

```
외부 (브라우저)
    │
    │  http://localhost
    ▼
┌─────────────────────────────────────────────┐
│           Ingress (ingress002)              │
│         외부 요청을 경로별로 분배               │
│                                             │
│   /            → vue002ser (8000)           │
│   /boot/...    → boot002ser (8001)          │
└──────┬──────────────────┬───────────────────┘
       │                  │
       ▼                  ▼
┌──────────────┐  ┌──────────────┐
│  Service     │  │  Service     │
│  vue002ser   │  │  boot002ser  │
│  포트: 8000   │  │  포트: 8001   │
│  ClusterIP   │  │  ClusterIP   │
└──────┬───────┘  └──────┬───────┘
       │                  │
       ▼                  ▼
┌──────────────┐  ┌──────────────┐
│  Deployment  │  │  Deployment  │
│  vue002dep   │  │  boot002dep  │
│              │  │              │
│  ┌────────┐  │  │  ┌────────┐  │
│  │  Pod   │  │  │  │  Pod   │  │
│  │ nginx  │  │  │  │ Spring │  │
│  │ :80    │  │  │  │ :8080  │  │
│  └────────┘  │  └────────┘  │
└──────────────┘  └──────────────┘
```

---

## 파일별 상세 설명

### 1. `vue002dep.yml` — Vue 프론트엔드 배포

- `limraynor/k8s-vue-ing` 이미지로 Pod 생성
- nginx가 **80포트**에서 빌드된 Vue 정적 파일을 서빙
- replicas 기본값 1개

### 2. `vue002ser.yml` — Vue 내부 네트워크

- **ClusterIP** 타입 → 클러스터 내부에서만 접근 가능 (외부 직접 접근 불가)
- `8000` 포트로 들어오면 → Pod의 `80` 포트로 전달

### 3. `boot002dep.yml` — Boot 백엔드 배포

- `limraynor/k8s-boot-ing` 이미지로 Pod 생성
- Spring Boot가 **8080포트**에서 실행
- replicas 1개

### 4. `boot002ser.yml` — Boot 내부 네트워크

- **ClusterIP** 타입 → 클러스터 내부에서만 접근 가능
- `8001` 포트로 들어오면 → Pod의 `8080` 포트로 전달

### 5. `ingress002.yml` — 외부 진입점 (가장 중요)

- **유일하게 외부와 연결되는 파일**
- URL 경로에 따라 요청을 분배:

| 요청 URL | 전달 대상 | 예시 |
|---|---|---|
| `/`, `/about` 등 | vue002ser:8000 | 프론트엔드 페이지 |
| `/boot/plus`, `/boot/health` 등 | boot002ser:8001 | 백엔드 API |

- `/boot/plus` 요청 시 → `rewrite-target: /$2` 에 의해 `/boot` 부분이 제거되어 → Boot 서버에는 `/plus`로 전달됨

---

## 요청 흐름 예시

```
브라우저: http://localhost/boot/plus?num1=10&num2=20
    │
    ▼
Ingress: /boot로 시작 → boot002ser:8001로 전달
         rewrite로 /boot 제거 → /plus?num1=10&num2=20
    │
    ▼
Service (boot002ser): 8001 → Pod의 8080으로 전달
    │
    ▼
Pod (Spring Boot): /plus 처리 → {"num1":10,"num2":20,"sum":30}
    │
    ▼
브라우저: 결과 수신
```

---

## 각 계층 역할 비유

 **Deployment** = 실제 일하는 직원 (Pod을 생성하고 관리) / replicaset, pod 에 대한 선언적 업데이트
- **Replicaset** 영속성과 비슷하게 pod가 종료되도 다시 시작해서 개수 유지 (레플리카 개수 관리)
- **Replica** = Pod의 복제품 (똑같은 pod기 여러개 존재)
- 
- **Service** = 요청에 맞는 타겟주소로 적절한 파드로 분배하는 로드밸런서의 역할
- **Service** = 내부 안내원 (클러스터 내부 네트워크 연결)
-
- **Ingress** = 단일 진입정 제공, url 라우팅 
- **Ingress** = 교통경찰 (외부 요청을 경로별로 분배)
