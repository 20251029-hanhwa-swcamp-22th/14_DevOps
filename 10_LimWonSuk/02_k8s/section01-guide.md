# Section 01 — NodePort로 기본 배포하기

## 이 섹션에서 배우는 것

- Kubernetes의 가장 기본 리소스: **Deployment**와 **Service**
- 외부에서 클러스터 내부 Pod에 접근하는 방법: **NodePort**
- 프론트엔드(Vue)와 백엔드(Spring Boot)를 각각 독립적으로 배포하는 방법

---

## 전체 구조도

```
외부 (브라우저)
    │
    ├── http://워커노드IP:30000  ──→  Vue 프론트엔드
    │
    └── http://워커노드IP:30001  ──→  Spring Boot 백엔드


┌──────────────────────────────────────────────────┐
│                Kubernetes Cluster                 │
│                                                  │
│  ┌─────────────────┐    ┌─────────────────┐     │
│  │ Service          │    │ Service          │     │
│  │ vue001ser        │    │ boot001ser       │     │
│  │ NodePort: 30000  │    │ NodePort: 30001  │     │
│  │ port: 8000       │    │ port: 8001       │     │
│  └────────┬─────────┘    └────────┬─────────┘     │
│           │                       │               │
│           ▼                       ▼               │
│  ┌─────────────────┐    ┌─────────────────┐     │
│  │ Deployment       │    │ Deployment       │     │
│  │ vue001dep        │    │ boot001dep       │     │
│  │                  │    │ replicas: 3      │     │
│  │ ┌──────────┐    │    │ ┌──────────┐    │     │
│  │ │   Pod    │    │    │ │  Pod  x3 │    │     │
│  │ │ nginx    │    │    │ │ Spring   │    │     │
│  │ │ :5173    │    │    │ │ :8080    │    │     │
│  │ └──────────┘    │    │ └──────────┘    │     │
│  └─────────────────┘    └─────────────────┘     │
└──────────────────────────────────────────────────┘
```

---

## 파일 목록 및 역할

| 파일 | 종류 | 역할 |
|---|---|---|
| `boot001dep.yml` | Deployment | Spring Boot 백엔드 Pod 3개 생성 |
| `boot001ser.yml` | Service (NodePort) | 외부에서 30001 포트로 백엔드 접근 |
| `vue001dep.yml` | Deployment | Vue 프론트엔드 Pod 1개 생성 |
| `vue001ser.yml` | Service (NodePort) | 외부에서 30000 포트로 프론트엔드 접근 |

---

## 파일별 상세 설명

### 1. `boot001dep.yml` — Spring Boot 백엔드 배포

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: boot001dep
spec:
  selector:
    matchLabels:
      app: boot001kube
  replicas: 3
  template:
    metadata:
      labels:
        app: boot001kube
    spec:
      containers:
        - name: boot-container
          image: limraynor/k8s-boot:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
```

**핵심 설명:**

| 항목 | 값 | 설명 |
|---|---|---|
| `kind: Deployment` | - | Pod를 관리하는 상위 리소스 |
| `replicas: 3` | 3개 | 동일한 Pod를 3개 생성하여 부하 분산 |
| `selector.matchLabels` | `app: boot001kube` | 이 라벨을 가진 Pod만 관리 대상 |
| `template.metadata.labels` | `app: boot001kube` | Pod에 붙이는 라벨 (selector와 반드시 일치해야 함) |
| `image` | `limraynor/k8s-boot:latest` | Docker Hub에서 가져올 이미지 |
| `imagePullPolicy: Always` | - | 매번 최신 이미지를 다시 당겨옴 |
| `containerPort: 8080` | - | 컨테이너 내부에서 Spring Boot가 실행되는 포트 |

**Deployment → ReplicaSet → Pod 관계:**
```
Deployment (boot001dep)
    └── ReplicaSet (자동 생성)
        ├── Pod 1 (boot-container, :8080)
        ├── Pod 2 (boot-container, :8080)
        └── Pod 3 (boot-container, :8080)
```
- Deployment가 ReplicaSet을 자동으로 생성
- ReplicaSet이 `replicas: 3`에 맞게 Pod 3개를 유지
- Pod가 죽으면 ReplicaSet이 자동으로 새 Pod를 생성

---

### 2. `boot001ser.yml` — Spring Boot 서비스 (NodePort)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: boot001ser
spec:
  type: NodePort
  ports:
    - port: 8001
      targetPort: 8080
      protocol: TCP
      nodePort: 30001
  selector:
    app: boot001kube
```

**포트 흐름 이해하기:**

```
외부 브라우저                     Service                         Pod
http://노드IP:30001  ──→  nodePort:30001 → port:8001 → targetPort:8080
```

| 포트 종류 | 값 | 설명 |
|---|---|---|
| `nodePort: 30001` | 외부 포트 | 클러스터 외부에서 접근하는 포트 (30000~32767 범위) |
| `port: 8001` | 서비스 포트 | 클러스터 내부에서 서비스에 접근하는 포트 |
| `targetPort: 8080` | Pod 포트 | 실제 컨테이너가 수신하는 포트 |

**selector의 역할:**
- `app: boot001kube` 라벨을 가진 Pod를 자동으로 찾아서 연결
- Deployment에서 만든 3개의 Pod 중 하나로 트래픽을 **자동 분배 (로드밸런싱)**

---

### 3. `vue001dep.yml` — Vue 프론트엔드 배포

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vue001dep
spec:
  selector:
    matchLabels:
      app: vue001kube
  template:
    metadata:
      labels:
        app: vue001kube
    spec:
      containers:
        - name: vue-container
          image: limraynor/k8s-vue:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 5173
```

**백엔드 Deployment와의 차이점:**

| 항목 | 백엔드 (boot001dep) | 프론트엔드 (vue001dep) |
|---|---|---|
| replicas | 3개 (명시) | 1개 (기본값) |
| 이미지 | limraynor/k8s-boot | limraynor/k8s-vue |
| 컨테이너 포트 | 8080 (Spring Boot) | 5173 (Vite 개발 서버) |

> **참고:** `replicas`를 명시하지 않으면 기본값 1개로 생성됩니다.

---

### 4. `vue001ser.yml` — Vue 서비스 (NodePort)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: vue001ser
spec:
  type: NodePort
  ports:
    - port: 8000
      targetPort: 5173
      protocol: TCP
      nodePort: 30000
  selector:
    app: vue001kube
```

**포트 흐름:**
```
외부 브라우저                     Service                         Pod
http://노드IP:30000  ──→  nodePort:30000 → port:8000 → targetPort:5173
```

---

## 실습 명령어

```bash
# 1. 리소스 생성 (모든 yml 파일 적용)
kubectl apply -f section01/

# 2. 배포 상태 확인
kubectl get deployments
kubectl get pods
kubectl get services

# 3. 상세 정보 확인
kubectl describe deployment boot001dep
kubectl describe service boot001ser

# 4. 브라우저에서 접근
#    Vue 프론트엔드: http://워커노드IP:30000
#    Boot 백엔드:    http://워커노드IP:30001/health
#    덧셈 API:      http://워커노드IP:30001/plus?num1=10&num2=20

# 5. 리소스 삭제
kubectl delete -f section01/
```

---

## Vue → Boot API 호출 흐름 (Section01 방식)

```
Vue (브라우저)
    │
    │  axios.get("http://localhost:30001/plus?num1=10&num2=20")
    │  ← NodePort를 직접 호출 (하드코딩)
    ▼
Service (boot001ser)
    │  nodePort:30001 → targetPort:8080
    ▼
Pod (Spring Boot)
    │  /plus 핸들러 실행
    │  결과: {"num1":10, "num2":20, "sum":30}
    ▼
Vue (브라우저)
    │  result.value = data.sum
    └── 화면에 "10 + 20 = 30" 출력
```

---

## NodePort 방식의 한계

| 문제점 | 설명 |
|---|---|
| **포트 충돌** | 30000~32767 범위의 포트를 수동 관리해야 함 |
| **URL 하드코딩** | 프론트에서 `http://localhost:30001`처럼 직접 지정해야 함 |
| **서비스 분리** | 프론트와 백엔드가 다른 포트 → CORS 설정 필수 |
| **보안 취약** | 모든 서비스가 외부에 직접 노출됨 |
| **확장성 부족** | 서비스가 늘어날수록 포트 관리가 복잡해짐 |

> **해결책:** Section 02에서 배우는 **ClusterIP + Ingress** 패턴으로 이 문제들을 해결합니다.

---

## 핵심 개념 정리

### Deployment란?
- Pod의 **선언적 배포**를 관리하는 리소스
- "이 이미지로 Pod를 N개 유지해줘"라고 선언하면 쿠버네티스가 알아서 관리
- Pod가 죽으면 자동 재생성 (자가 치유, Self-healing)

### Service란?
- Pod에 접근하기 위한 **고정된 네트워크 엔드포인트**
- Pod는 생성/삭제될 때마다 IP가 바뀌지만, Service는 고정 IP 제공
- selector로 라벨이 일치하는 Pod들을 자동으로 연결

### NodePort란?
- Service의 타입 중 하나
- 클러스터의 **모든 워커 노드**에서 지정한 포트로 접근 가능
- 개발/테스트 환경에서 가장 간단한 외부 접근 방법
- 포트 범위: 30000 ~ 32767

### Label과 Selector의 관계
```
Deployment (selector: app=boot001kube)
    ↕ 매칭
Pod (label: app=boot001kube)
    ↕ 매칭
Service (selector: app=boot001kube)
```
- **Label**: Pod에 붙이는 이름표
- **Selector**: "이 이름표를 가진 Pod를 찾아라"
- Deployment와 Service가 같은 selector를 사용해야 올바르게 연결됨

---

## 다음 단계 (Section 02 미리보기)

Section 01의 NodePort 한계를 극복하기 위해 Section 02에서는:
1. Service 타입을 **ClusterIP**로 변경 (외부 직접 노출 X)
2. **Ingress**를 추가하여 URL 경로 기반 라우팅
3. 프론트엔드와 백엔드를 **하나의 도메인**에서 경로로 분리
4. CORS 문제 해결 (같은 Origin에서 요청)
