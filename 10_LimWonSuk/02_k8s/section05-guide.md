# Section 05 — ConfigMap & Secret (설정 외부화)

## 이 섹션에서 배우는 것

- 애플리케이션 설정을 코드와 **분리**하여 관리하는 방법
- **ConfigMap** — 비밀이 아닌 일반 설정값 (DB URL, 타임아웃 등)
- **Secret** — 민감한 정보 (비밀번호, API 키 등)
- Pod에서 환경 변수로 주입받아 사용하는 방법

---

## 왜 설정을 외부화해야 하는가?

### 설정을 코드에 직접 넣으면?

```
❌ 나쁜 예: application.properties에 하드코딩
spring.datasource.url=jdbc:mariadb://localhost:3306/menudb
spring.datasource.username=swcamp
spring.datasource.password=swcamp123
```

**문제점:**
1. 환경(개발/스테이징/운영)마다 이미지를 **다시 빌드**해야 함
2. 비밀번호가 **소스 코드에 노출** (Git에 올라감)
3. 설정 변경 시마다 **재배포** 필요

### 설정을 외부에서 주입하면?

```
✅ 좋은 예: 환경 변수로 주입
DATABASE_URL  → ConfigMap에서 제공
USERNAME      → Secret에서 제공
PASSWORD      → Secret에서 제공
```

**장점:**
1. **같은 이미지**로 모든 환경에서 실행 가능
2. 비밀번호가 코드에 포함되지 않음
3. 설정만 변경하고 Pod만 재시작하면 됨

---

## 전체 구조도

```
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                        │
│                                                             │
│  ┌──────────────────┐     ┌──────────────────┐             │
│  │    ConfigMap      │     │     Secret       │             │
│  │    db-config      │     │     db-secret    │             │
│  │                   │     │                  │             │
│  │  database_url:    │     │  username: ****  │             │
│  │    jdbc:maria...  │     │  password: ****  │             │
│  │  timeout: 30      │     │  (Base64 인코딩)  │             │
│  └────────┬──────────┘     └────────┬─────────┘             │
│           │                         │                       │
│           │  환경변수로 주입          │  환경변수로 주입        │
│           ▼                         ▼                       │
│  ┌────────────────────────────────────────────┐             │
│  │           Deployment (boot005dep)          │             │
│  │                                            │             │
│  │  ┌──────────────────────────────────────┐  │             │
│  │  │            Pod (Spring Boot)         │  │             │
│  │  │                                      │  │             │
│  │  │  환경변수:                            │  │             │
│  │  │  DATABASE_URL = jdbc:mariadb://...   │  │             │
│  │  │  TIMEOUT      = 30                   │  │             │
│  │  │  USERNAME     = swcamp   (디코딩됨)   │  │             │
│  │  │  PASSWORD     = swcamp   (디코딩됨)   │  │             │
│  │  └──────────────────────────────────────┘  │             │
│  └────────────────────────────────────────────┘             │
└─────────────────────────────────────────────────────────────┘
```

---

## 파일 목록 및 역할

| 파일 | 종류 | 역할 |
|---|---|---|
| `configmap.yaml` | ConfigMap | DB URL, 타임아웃 등 일반 설정 저장 |
| `secret.yaml` | Secret | DB 사용자명, 비밀번호 등 민감 정보 저장 |
| `boot005dep.yaml` | Deployment | ConfigMap/Secret을 환경변수로 주입받는 Pod 생성 |

---

## 파일별 상세 설명

### 1. `configmap.yaml` — 일반 설정값

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: db-config
data:
  database_url: "jdbc:mariadb://localhost:3306/menudb"
  timeout: "30"
```

**특징:**
- **평문(Plain Text)**으로 저장
- 비밀이 아닌 설정값을 저장하는 데 사용
- Key-Value 형식

| Key | Value | 용도 |
|---|---|---|
| `database_url` | jdbc:mariadb://localhost:3306/menudb | DB 접속 URL |
| `timeout` | 30 | 요청 타임아웃 (초) |

**ConfigMap 생성 방법 (2가지):**

```bash
# 방법 1: YAML 파일로 생성
kubectl apply -f section05/configmap.yaml

# 방법 2: 명령어로 직접 생성
kubectl create configmap db-config \
  --from-literal=database_url="jdbc:mariadb://localhost:3306/menudb" \
  --from-literal=timeout="30"
```

---

### 2. `secret.yaml` — 민감한 설정값

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
type: Opaque
data:
  username: c3djYW1w        # Base64 인코딩된 값
  password: c3djYW1w        # Base64 인코딩된 값
```

**Base64 인코딩/디코딩:**

```bash
# 인코딩 (평문 → Base64)
echo -n "swcamp" | base64
# 결과: c3djYW1w

# 디코딩 (Base64 → 평문)
echo "c3djYW1w" | base64 --decode
# 결과: swcamp
```

> **중요:** Base64는 **암호화가 아닌 인코딩**입니다!
> 누구나 디코딩할 수 있으므로 Secret만으로는 보안이 완벽하지 않습니다.
> 실제 프로덕션에서는 **RBAC** + **etcd 암호화** + **Vault** 등을 함께 사용합니다.

**Secret의 type 종류:**

| Type | 용도 |
|---|---|
| `Opaque` | 일반적인 Key-Value (가장 많이 사용) |
| `kubernetes.io/dockerconfigjson` | Docker Registry 인증 정보 |
| `kubernetes.io/tls` | TLS 인증서 |
| `kubernetes.io/basic-auth` | 기본 인증 (username/password) |

**Secret 생성 방법 (2가지):**

```bash
# 방법 1: YAML 파일로 생성 (Base64 인코딩 필요)
kubectl apply -f section05/secret.yaml

# 방법 2: 명령어로 직접 생성 (자동으로 Base64 인코딩)
kubectl create secret generic db-secret \
  --from-literal=username=swcamp \
  --from-literal=password=swcamp
```

---

### 3. `boot005dep.yaml` — ConfigMap/Secret을 사용하는 Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: boot005dep
spec:
  replicas: 1
  selector:
    matchLabels:
      app: boot005kube
  template:
    metadata:
      labels:
        app: boot005kube
    spec:
      containers:
        - name: boot-container
          image: limraynor/k8s-boot-ing:latest
          imagePullPolicy: Always
          env:
            # ConfigMap에서 값 가져오기
            - name: DATABASE_URL
              valueFrom:
                configMapKeyRef:
                  name: db-config
                  key: database_url
            - name: TIMEOUT
              valueFrom:
                configMapKeyRef:
                  name: db-config
                  key: timeout

            # Secret에서 값 가져오기
            - name: USERNAME
              valueFrom:
                secretKeyRef:
                  name: db-secret
                  key: username
            - name: PASSWORD
              valueFrom:
                secretKeyRef:
                  name: db-secret
                  key: password
```

**환경 변수 주입 흐름:**

```
ConfigMap (db-config)                    Pod 환경변수
┌──────────────────────┐
│ database_url: jdbc:..│ ──→ DATABASE_URL = jdbc:mariadb://localhost:3306/menudb
│ timeout: 30          │ ──→ TIMEOUT      = 30
└──────────────────────┘

Secret (db-secret)                       Pod 환경변수
┌──────────────────────┐
│ username: c3djYW1w   │ ──→ USERNAME = swcamp  (자동 디코딩!)
│ password: c3djYW1w   │ ──→ PASSWORD = swcamp  (자동 디코딩!)
└──────────────────────┘
```

**Spring Boot에서 사용:**

```java
// application.properties에서 환경변수 참조
// spring.datasource.url=${DATABASE_URL}
// spring.datasource.username=${USERNAME}
// spring.datasource.password=${PASSWORD}

// 또는 Java 코드에서 직접 사용
@Value("${DATABASE_URL}")
private String databaseUrl;

// 또는 System.getenv()로 접근
String username = System.getenv("USERNAME");
```

---

## ConfigMap vs Secret 비교

| 항목 | ConfigMap | Secret |
|---|---|---|
| **용도** | 일반 설정값 | 민감한 정보 |
| **저장 형태** | 평문 (Plain Text) | Base64 인코딩 |
| **etcd 저장** | 평문 | 기본 평문 (암호화 옵션 가능) |
| **YAML data 형식** | 그대로 기입 | Base64로 변환하여 기입 |
| **kubectl 조회** | 값이 그대로 보임 | 값이 숨겨짐 |
| **예시** | DB URL, 포트, 타임아웃 | 비밀번호, API 키, 인증 토큰 |

---

## 환경변수 주입 방식 비교

### 방식 1: 개별 Key 참조 (이번 실습에서 사용)

```yaml
env:
  - name: DATABASE_URL
    valueFrom:
      configMapKeyRef:
        name: db-config
        key: database_url
```
- ConfigMap의 **특정 Key만** 선택하여 주입
- 환경변수 이름을 자유롭게 지정 가능

### 방식 2: 전체 Key 한번에 주입

```yaml
envFrom:
  - configMapRef:
      name: db-config
  - secretRef:
      name: db-secret
```
- ConfigMap/Secret의 **모든 Key**가 환경변수로 주입
- Key 이름이 그대로 환경변수 이름이 됨
- 간편하지만 이름 충돌에 주의

### 방식 3: 볼륨으로 마운트 (파일로 주입)

```yaml
volumes:
  - name: config-volume
    configMap:
      name: db-config
volumeMounts:
  - name: config-volume
    mountPath: /etc/config
```
- 각 Key가 **파일**로 생성됨
- `/etc/config/database_url` → 파일 내용: `jdbc:mariadb://...`
- `/etc/config/timeout` → 파일 내용: `30`
- 설정 파일을 통째로 주입할 때 유용

---

## 실습 명령어

```bash
# 1. ConfigMap 생성
kubectl apply -f section05/configmap.yaml

# 2. Secret 생성
kubectl apply -f section05/secret.yaml

# 3. ConfigMap 확인 (값이 그대로 보임)
kubectl get configmap db-config -o yaml
# data:
#   database_url: jdbc:mariadb://localhost:3306/menudb
#   timeout: "30"

# 4. Secret 확인 (Base64로 보임)
kubectl get secret db-secret -o yaml
# data:
#   username: c3djYW1w
#   password: c3djYW1w

# 5. Secret 값 디코딩해서 확인
kubectl get secret db-secret -o jsonpath='{.data.username}' | base64 --decode
# swcamp

# 6. Deployment 생성
kubectl apply -f section05/boot005dep.yaml

# 7. Pod에서 환경변수 확인
kubectl exec -it <pod-name> -- env | grep -E "DATABASE_URL|TIMEOUT|USERNAME|PASSWORD"
# DATABASE_URL=jdbc:mariadb://localhost:3306/menudb
# TIMEOUT=30
# USERNAME=swcamp
# PASSWORD=swcamp

# 8. 리소스 삭제
kubectl delete -f section05/
```

---

## ConfigMap/Secret 변경 시 주의사항

| 상황 | 환경변수 방식 | 볼륨 마운트 방식 |
|---|---|---|
| ConfigMap 값 변경 | Pod **재시작 필요** | 자동 반영 (약간의 딜레이) |
| Secret 값 변경 | Pod **재시작 필요** | 자동 반영 (약간의 딜레이) |

```bash
# ConfigMap 값 변경 후 Pod 재시작
kubectl edit configmap db-config
kubectl rollout restart deployment boot005dep
```

---

## 핵심 개념 정리

### ConfigMap이란?
- **비밀이 아닌 설정 데이터**를 Key-Value 형태로 저장하는 리소스
- 환경별(개발/스테이징/운영) 설정을 분리하여 관리
- 같은 이미지로 다양한 환경에서 실행 가능 (12-Factor App 원칙)

### Secret이란?
- **민감한 정보**를 저장하는 리소스
- Base64 인코딩으로 저장 (암호화는 아님!)
- `kubectl get secret`으로 조회 시 값이 숨겨짐

### 환경변수 주입이란?
- 컨테이너 시작 시 **외부에서 값을 전달**하는 방식
- 코드 변경 없이 동작을 바꿀 수 있음
- ConfigMap → `configMapKeyRef`
- Secret → `secretKeyRef`

---

## Section 01 → 02 → 03 → 04 → 05 흐름 정리

```
Section 01: 기본 배포 (NodePort)
Section 02: 프로덕션 아키텍처 (ClusterIP + Ingress)
Section 03: 무중단 배포 (Rolling Update)
Section 04: 영구 저장소 (PV/PVC)
Section 05: 설정 외부화 (ConfigMap & Secret)  ← 현재
    └── 환경별 설정을 코드와 분리
    └── 비밀번호 등 민감 정보 안전하게 관리
    └── 다음: 컨테이너 상태 자동 감시 (Section 06)
```
