# Section 03 — Rolling Update (무중단 배포)

## 이 섹션에서 배우는 것

- 서비스 중단 없이 애플리케이션을 업데이트하는 **Rolling Update** 전략
- 이미지 **버전 태그**(v1, v2)를 사용한 배포 관리
- `maxUnavailable`과 `maxSurge` 옵션의 의미와 동작 방식

---

## 전체 구조도

```
외부 (브라우저)
    │
    │  http://localhost
    ▼
┌─────────────────────────────────────────────┐
│           Ingress (ingress003)              │
│         /  → vue003ser (8000)               │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│         Service (vue003ser)          │
│         ClusterIP / port:8000        │
└──────────────────┬───────────────────┘
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
   ┌─────────┐ ┌─────────┐ ┌─────────┐
   │  Pod 1  │ │  Pod 2  │ │  Pod 3  │
   │  v1→v2  │ │  v1→v2  │ │  v1→v2  │
   │  :80    │ │  :80    │ │  :80    │
   └─────────┘ └─────────┘ └─────────┘
        ↑          ↑          ↑
        └──────────┴──────────┘
          Deployment (vue003dep)
          strategy: RollingUpdate
```

---

## 파일 목록 및 역할

| 파일 | 종류 | 역할 |
|---|---|---|
| `vue003dep.yml` | Deployment | Vue Pod 3개 생성 + Rolling Update 전략 설정 |
| `vue003ser.yml` | Service | Vue Pod 내부 네트워크 (ClusterIP) |
| `ingress003.yml` | Ingress | 외부 요청을 Vue 서비스로 라우팅 |

---

## 파일별 상세 설명

### 1. `vue003dep.yml` — Rolling Update 핵심 파일

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vue003dep
spec:
  replicas: 3
  selector:
    matchLabels:
      app: vue003kube
  template:
    metadata:
      labels:
        app: vue003kube
    spec:
      containers:
        - name: vue-container
          image: limraynor/k8s-vue-ing:v1
          imagePullPolicy: Always
          ports:
            - containerPort: 80
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
```

**Section 02와 달라진 점:**

| 항목 | Section 02 | Section 03 |
|---|---|---|
| replicas | 1개 | **3개** (Rolling Update를 위해 필수) |
| image 태그 | `:latest` | **`:v1`** (버전 명시) |
| strategy | 없음 (기본값) | **RollingUpdate 명시** |

**strategy 옵션 상세:**

| 옵션 | 값 | 의미 |
|---|---|---|
| `maxUnavailable: 1` | 최대 1개 | 업데이트 중 **최대 1개의 Pod가 내려갈 수 있음** → 최소 2개는 항상 실행 |
| `maxSurge: 1` | 최대 1개 | 업데이트 중 **최대 1개의 추가 Pod를 생성**할 수 있음 → 최대 4개까지 존재 가능 |

---

### 2. `vue003ser.yml` — 서비스

```yaml
apiVersion: v1
kind: Service
metadata:
  name: vue003ser
spec:
  type: ClusterIP
  ports:
    - port: 8000
      targetPort: 80
  selector:
    app: vue003kube
```

- Section 02와 동일한 구조 (ClusterIP 타입)
- Rolling Update 동안 Service는 변경 없이 유지됨
- 준비된(Ready) Pod에만 트래픽을 자동으로 전달

---

### 3. `ingress003.yml` — Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ingress003
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "false"
    nginx.ingress.kubernetes.io/rewrite-target: /$2
spec:
  ingressClassName: nginx
  rules:
    - http:
        paths:
          - path: /()(.*)
            pathType: ImplementationSpecific
            backend:
              service:
                name: vue003ser
                port:
                  number: 8000
```

- Vue 프론트엔드만 라우팅 (백엔드 경로 없음)
- Section 02 Ingress의 간소화 버전

---

## Rolling Update 동작 과정

### v1에서 v2로 업데이트할 때

```
시간 ──────────────────────────────────────────────────→

[초기 상태] replicas=3, 모두 v1
┌──────┐ ┌──────┐ ┌──────┐
│ v1 ✅ │ │ v1 ✅ │ │ v1 ✅ │   ← 3개 모두 v1, 정상 서비스 중
└──────┘ └──────┘ └──────┘

[Step 1] 새 Pod 1개 생성 (maxSurge=1, 총 4개)
┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
│ v1 ✅ │ │ v1 ✅ │ │ v1 ✅ │ │ v2 🔄│   ← v2 Pod 생성 중
└──────┘ └──────┘ └──────┘ └──────┘

[Step 2] v2 준비 완료 → 기존 v1 1개 종료 (maxUnavailable=1, 총 3개)
┌──────┐ ┌──────┐ ┌──────┐
│ v1 ✅ │ │ v1 ✅ │ │ v2 ✅ │   ← v1 1개 제거, v2 1개 서비스 시작
└──────┘ └──────┘ └──────┘

[Step 3] 다시 새 v2 Pod 생성 (총 4개)
┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
│ v1 ✅ │ │ v1 ✅ │ │ v2 ✅ │ │ v2 🔄│
└──────┘ └──────┘ └──────┘ └──────┘

[Step 4] v2 준비 완료 → v1 1개 종료 (총 3개)
┌──────┐ ┌──────┐ ┌──────┐
│ v1 ✅ │ │ v2 ✅ │ │ v2 ✅ │
└──────┘ └──────┘ └──────┘

[Step 5~6] 같은 과정 반복

[최종 상태] 모두 v2
┌──────┐ ┌──────┐ ┌──────┐
│ v2 ✅ │ │ v2 ✅ │ │ v2 ✅ │   ← 3개 모두 v2, 무중단 완료!
└──────┘ └──────┘ └──────┘
```

**핵심 포인트:**
- 전체 과정에서 **최소 2개의 Pod가 항상 실행** 중 → 서비스 중단 없음
- 사용자는 업데이트 과정을 **전혀 느끼지 못함**

---

## 실습 명령어

### 1단계: 최초 배포 (v1)

```bash
# v1 이미지로 배포
kubectl apply -f section03/

# Pod 상태 확인
kubectl get pods -l app=vue003kube
# NAME                         READY   STATUS    RESTARTS   AGE
# vue003dep-xxxxx-abc1         1/1     Running   0          10s
# vue003dep-xxxxx-abc2         1/1     Running   0          10s
# vue003dep-xxxxx-abc3         1/1     Running   0          10s

# 현재 이미지 버전 확인
kubectl describe deployment vue003dep | grep Image
# Image: limraynor/k8s-vue-ing:v1
```

### 2단계: Rolling Update 실행 (v1 → v2)

```bash
# 방법 1: 이미지 직접 변경 (가장 간편)
kubectl set image deployment/vue003dep vue-container=limraynor/k8s-vue-ing:v2

# 방법 2: yml 파일 수정 후 다시 적용
# vue003dep.yml에서 image: limraynor/k8s-vue-ing:v2 로 변경
kubectl apply -f section03/vue003dep.yml
```

### 3단계: 업데이트 과정 실시간 모니터링

```bash
# Pod 변화 실시간 관찰 (가장 중요한 명령어!)
kubectl get pods -l app=vue003kube -w

# 출력 예시:
# NAME                         READY   STATUS              RESTARTS   AGE
# vue003dep-abc123-x1          1/1     Running             0          5m
# vue003dep-abc123-x2          1/1     Running             0          5m
# vue003dep-abc123-x3          1/1     Running             0          5m
# vue003dep-def456-y1          0/1     ContainerCreating   0          2s    ← v2 생성 시작
# vue003dep-def456-y1          1/1     Running             0          5s    ← v2 준비 완료
# vue003dep-abc123-x1          1/1     Terminating         0          5m    ← v1 종료 시작
# vue003dep-def456-y2          0/1     ContainerCreating   0          1s    ← 다음 v2 생성
# ...

# 롤아웃 상태 확인
kubectl rollout status deployment/vue003dep
# Waiting for deployment "vue003dep" rollout to finish: 1 out of 3 new replicas have been updated...
# Waiting for deployment "vue003dep" rollout to finish: 2 out of 3 new replicas have been updated...
# deployment "vue003dep" successfully rolled out

# 롤아웃 히스토리 확인
kubectl rollout history deployment/vue003dep
```

### 4단계: 문제 발생 시 롤백

```bash
# 이전 버전으로 롤백 (v2 → v1)
kubectl rollout undo deployment/vue003dep

# 특정 버전으로 롤백
kubectl rollout undo deployment/vue003dep --to-revision=1

# 롤백 상태 확인
kubectl rollout status deployment/vue003dep
```

---

## 배포 전략 비교

### RollingUpdate vs Recreate

| 항목 | RollingUpdate | Recreate |
|---|---|---|
| **동작 방식** | Pod를 하나씩 교체 | 기존 Pod 전부 삭제 후 새로 생성 |
| **서비스 중단** | 없음 (무중단) | 있음 (다운타임 발생) |
| **리소스 사용** | 일시적으로 더 많은 Pod 필요 | 기존과 동일 |
| **사용 시점** | 프로덕션 환경 (대부분) | DB 마이그레이션 등 동시 실행 불가한 경우 |

```yaml
# Recreate 전략 예시 (참고용)
strategy:
  type: Recreate
# → 모든 v1 Pod 삭제 → 모든 v2 Pod 생성 (중간에 서비스 중단)
```

---

## maxUnavailable과 maxSurge 조합 예시

replicas=3 기준:

| maxUnavailable | maxSurge | 최소 Pod | 최대 Pod | 특징 |
|---|---|---|---|---|
| 1 | 1 | 2 | 4 | **균형잡힌 설정** (기본 추천) |
| 0 | 1 | 3 | 4 | 안전 최우선 (항상 3개 유지) |
| 1 | 0 | 2 | 3 | 리소스 절약 (추가 Pod 없음) |
| 2 | 2 | 1 | 5 | 빠른 업데이트 (리소스 여유 필요) |

> **주의:** `maxUnavailable: 0`, `maxSurge: 0`은 불가능 (업데이트 자체가 안 됨)

---

## 이미지 버전 태그의 중요성

### `:latest` vs `:v1`, `:v2`

| 태그 | 장점 | 단점 |
|---|---|---|
| `:latest` | 편리 | 어떤 버전인지 추적 불가, 롤백 어려움 |
| `:v1`, `:v2` | 버전 추적 가능, 롤백 명확 | 배포 시 태그 관리 필요 |

**프로덕션 권장:** 반드시 버전 태그 사용!

```bash
# Docker 이미지 빌드 시 버전 태그 지정
docker build -t limraynor/k8s-vue-ing:v1 .
docker build -t limraynor/k8s-vue-ing:v2 .

# Docker Hub에 푸시
docker push limraynor/k8s-vue-ing:v1
docker push limraynor/k8s-vue-ing:v2
```

---

## 핵심 개념 정리

### Rolling Update란?
- 기존 Pod를 **점진적으로 교체**하여 서비스 중단 없이 업데이트하는 전략
- Kubernetes Deployment의 **기본 배포 전략**
- 항상 최소한의 Pod가 실행되어 사용자 요청을 처리

### 왜 replicas가 여러 개 필요한가?
- 1개 Pod만 있으면 교체 시 순간적으로 서비스가 중단될 수 있음
- **최소 2개 이상**이어야 하나를 교체하는 동안 나머지가 트래픽 처리
- 일반적으로 **3개**를 권장 (안정성과 리소스 효율의 균형)

### 롤백(Rollback)이란?
- 업데이트 후 문제가 발생하면 **이전 버전으로 되돌리는 것**
- `kubectl rollout undo`로 즉시 실행 가능
- Kubernetes가 이전 ReplicaSet을 기억하고 있어 빠른 롤백 가능

---

## Section 01 → 02 → 03 흐름 정리

```
Section 01: 기본 배포 (NodePort)
    └── Pod를 만들고 외부에서 접근할 수 있게 함
    └── 문제: 포트 관리, CORS, 보안

Section 02: 프로덕션 아키텍처 (ClusterIP + Ingress)
    └── 내부 서비스 + 단일 진입점으로 구조 개선
    └── 문제: 배포 시 서비스 중단 가능

Section 03: 무중단 배포 (Rolling Update)  ← 현재
    └── 서비스 중단 없이 안전하게 업데이트
    └── 다음: 데이터 영속성 문제 해결 (Section 04)
```
