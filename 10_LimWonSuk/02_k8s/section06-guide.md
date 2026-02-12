# Section 06 — Probes (헬스 체크)

## 이 섹션에서 배우는 것

- 컨테이너가 **정상 작동** 중인지 자동으로 확인하는 방법
- **Liveness Probe** — 컨테이너가 살아있는지 확인 (죽으면 재시작)
- **Readiness Probe** — 컨테이너가 트래픽을 받을 준비가 되었는지 확인
- Probe 실패 시 Kubernetes가 **자동으로** 대응하는 방식

---

## 왜 헬스 체크가 필요한가?

### Probe 없이 운영하면?

```
Pod 상태: Running (컨테이너 프로세스는 살아있음)
하지만 내부적으로:
  - 애플리케이션이 무한 루프에 빠져 응답 불가 😱
  - DB 연결이 끊어져 에러만 반환 😱
  - 메모리 부족으로 요청 처리 불가 😱

Kubernetes는 "컨테이너 프로세스가 살아있으니 정상이다"라고 판단
→ 계속 트래픽을 보냄 → 사용자는 에러만 받음!
```

### Probe를 설정하면?

```
Liveness Probe가 주기적으로 /health 확인
  → 응답 없음 → 컨테이너 자동 재시작! ✅

Readiness Probe가 주기적으로 / 확인
  → 응답 없음 → Service에서 해당 Pod 제외! ✅
  → 다른 정상 Pod로만 트래픽 전달
```

---

## 전체 구조도

```
외부 (브라우저)
    │
    ▼
┌─────────────────────────────────┐
│      Service (vue006ser)        │
│      ClusterIP / port:8000      │
│                                 │
│  트래픽 분배 대상:               │
│  ✅ Ready Pod만 포함             │
│  ❌ Not Ready Pod 제외           │
└──────────────┬──────────────────┘
               │
    ┌──────────┼──────────┐
    ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌────────┐
│ Pod 1  │ │ Pod 2  │ │ Pod 3  │
│  ✅    │ │  ✅    │ │  ❌    │  ← Pod 3: Readiness 실패 → 트래픽 안 받음
│ :80    │ │ :80    │ │ :80    │
│        │ │        │ │        │
│Liveness│ │Liveness│ │Liveness│  ← Liveness 실패 시 → 재시작
│Readines│ │Readines│ │Readines│  ← Readiness 실패 시 → Service에서 제외
└────────┘ └────────┘ └────────┘
```

---

## 파일 목록 및 역할

| 파일 | 종류 | 역할 |
|---|---|---|
| `vue006dep.yaml` | Deployment | Liveness/Readiness Probe가 설정된 Vue Pod 생성 |
| `vue006ser.yaml` | Service | Vue Pod 내부 네트워크 (ClusterIP) |

---

## 파일별 상세 설명

### 1. `vue006dep.yaml` — Probe가 설정된 Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vue006dep
spec:
  replicas: 1
  selector:
    matchLabels:
      app: vue006kube
  template:
    metadata:
      labels:
        app: vue006kube
    spec:
      containers:
        - name: vue-container
          image: limraynor/k8s-vue-ing:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 80

          # Liveness Probe: 컨테이너가 살아있는가?
          livenessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 5
            periodSeconds: 5
            failureThreshold: 1

          # Readiness Probe: 트래픽을 받을 준비가 되었는가?
          readinessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 3
            periodSeconds: 5
            failureThreshold: 1
```

---

## Liveness Probe 상세

### 역할: "이 컨테이너가 살아있는가?"

```yaml
livenessProbe:
  httpGet:
    path: /          # GET 요청을 보낼 경로
    port: 80         # 요청 보낼 포트
  initialDelaySeconds: 5   # 컨테이너 시작 후 5초 대기
  periodSeconds: 5         # 5초마다 체크
  failureThreshold: 1      # 1번 실패하면 조치
```

**동작 타임라인:**

```
컨테이너 시작
    │
    │  5초 대기 (initialDelaySeconds: 5)
    │  (앱이 기동되는 시간을 줌)
    │
    ▼
[0초] GET / :80 → 200 OK ✅ (정상)
    │
    │  5초 대기 (periodSeconds: 5)
    │
    ▼
[5초] GET / :80 → 200 OK ✅ (정상)
    │
    │  5초 대기
    │
    ▼
[10초] GET / :80 → ❌ 실패 (응답 없음)
    │
    │  failureThreshold: 1 (1번 실패)
    │
    ▼
🔄 컨테이너 재시작! (kubelet이 자동으로 컨테이너를 재시작)
```

**실패 시 동작:**
- 컨테이너를 **재시작 (Restart)**
- Pod 자체는 삭제되지 않음
- `kubectl get pods`에서 RESTARTS 카운트가 증가

---

## Readiness Probe 상세

### 역할: "이 컨테이너가 트래픽을 받을 준비가 되었는가?"

```yaml
readinessProbe:
  httpGet:
    path: /          # GET 요청을 보낼 경로
    port: 80         # 요청 보낼 포트
  initialDelaySeconds: 3   # 컨테이너 시작 후 3초 대기
  periodSeconds: 5         # 5초마다 체크
  failureThreshold: 1      # 1번 실패하면 조치
```

**실패 시 동작:**
- 해당 Pod를 **Service의 Endpoint에서 제외**
- 트래픽이 해당 Pod로 **전달되지 않음**
- 컨테이너를 재시작하지는 않음 (Liveness와 다름!)
- 다시 성공하면 Endpoint에 **자동 복귀**

---

## Liveness vs Readiness 비교

| 항목 | Liveness Probe | Readiness Probe |
|---|---|---|
| **질문** | "죽었나?" | "준비됐나?" |
| **실패 시** | 컨테이너 **재시작** | Service에서 **제외** (재시작 X) |
| **목적** | 교착 상태, 무한 루프 감지 | 초기화 중, 과부하 상태 감지 |
| **비유** | 환자의 맥박 체크 | 식당의 "영업 중" 표시 |

### 함께 사용할 때의 시나리오

```
[상황 1] 앱 시작 중 (아직 초기화 안 됨)
  Liveness: ✅ (프로세스는 살아있음)
  Readiness: ❌ (아직 요청 처리 못함)
  → Service에서 제외, 재시작하지 않음
  → 초기화 완료 후 자동으로 Service에 복귀

[상황 2] 앱이 정상 동작 중
  Liveness: ✅
  Readiness: ✅
  → 정상적으로 트래픽 수신

[상황 3] 앱이 과부하 (일시적)
  Liveness: ✅ (프로세스는 살아있음)
  Readiness: ❌ (응답 시간 초과)
  → Service에서 제외 (트래픽 안 받음)
  → 부하 해소 후 Readiness 통과 → 자동 복귀

[상황 4] 앱이 완전히 멈춤 (데드락)
  Liveness: ❌ (응답 없음)
  Readiness: ❌
  → 컨테이너 재시작!
  → 재시작 후 Readiness 통과하면 Service에 복귀
```

---

### 2. `vue006ser.yaml` — 서비스

```yaml
apiVersion: v1
kind: Service
metadata:
  name: vue006ser
spec:
  type: ClusterIP
  ports:
    - port: 8000
      targetPort: 80
  selector:
    app: vue006kube
```

- Readiness Probe를 통과한 Pod만 Endpoint에 포함
- `kubectl get endpoints vue006ser`로 확인 가능

---

## Probe 종류 (체크 방법 3가지)

### 1. HTTP GET (이번 실습에서 사용)

```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 8080
```
- 지정 경로로 **HTTP GET 요청**
- 200~399 응답 → 성공
- 그 외 → 실패
- **웹 애플리케이션에 가장 적합**

### 2. TCP Socket

```yaml
livenessProbe:
  tcpSocket:
    port: 3306
```
- 지정 포트에 **TCP 연결** 시도
- 연결 성공 → 성공
- 연결 실패 → 실패
- **DB, Redis 등 HTTP가 아닌 서비스에 적합**

### 3. Exec (명령어 실행)

```yaml
livenessProbe:
  exec:
    command:
      - cat
      - /tmp/healthy
```
- 컨테이너 내부에서 **명령어 실행**
- 종료 코드 0 → 성공
- 종료 코드 0 이외 → 실패
- **커스텀 헬스 체크 로직에 적합**

---

## Probe 설정 옵션 상세

```yaml
livenessProbe:
  httpGet:
    path: /
    port: 80
  initialDelaySeconds: 5    # 최초 대기 시간
  periodSeconds: 5           # 체크 주기
  failureThreshold: 1        # 연속 실패 허용 횟수
  successThreshold: 1        # 연속 성공 필요 횟수 (Liveness는 항상 1)
  timeoutSeconds: 1          # 응답 대기 시간 (기본값 1초)
```

| 옵션 | 기본값 | 설명 |
|---|---|---|
| `initialDelaySeconds` | 0 | 컨테이너 시작 후 첫 Probe까지 대기 시간 |
| `periodSeconds` | 10 | Probe 실행 간격 |
| `failureThreshold` | 3 | 이 횟수만큼 연속 실패하면 조치 |
| `successThreshold` | 1 | 이 횟수만큼 연속 성공해야 정상 판정 |
| `timeoutSeconds` | 1 | 각 Probe의 응답 대기 시간 |

**권장 설정 (프로덕션):**

```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 8080
  initialDelaySeconds: 30    # 앱 기동에 충분한 시간 부여
  periodSeconds: 10          # 너무 자주 체크하면 부하 발생
  failureThreshold: 3        # 일시적 오류 허용
  timeoutSeconds: 5          # 네트워크 지연 고려

readinessProbe:
  httpGet:
    path: /ready
    port: 8080
  initialDelaySeconds: 5     # Liveness보다 먼저 시작
  periodSeconds: 5           # 더 자주 체크 (트래픽 제어이므로)
  failureThreshold: 1        # 즉시 제외
  timeoutSeconds: 3
```

---

## 실습: Probe 실패 시뮬레이션

### 실패 상황 만들기 (포트를 일부러 잘못 설정)

```yaml
# 일부러 잘못된 포트(8080)로 Probe 설정
livenessProbe:
  httpGet:
    path: /
    port: 8080    # 실제 nginx는 80포트 → 실패!
  initialDelaySeconds: 5
  periodSeconds: 5
  failureThreshold: 1
```

**결과:**

```bash
kubectl get pods -w
# NAME                         READY   STATUS    RESTARTS   AGE
# vue006dep-xxxxx              0/1     Running   0          5s
# vue006dep-xxxxx              0/1     Running   1          10s   ← 재시작!
# vue006dep-xxxxx              0/1     Running   2          15s   ← 또 재시작!
# vue006dep-xxxxx              0/1     CrashLoopBackOff  3  20s  ← 반복 재시작 백오프

kubectl describe pod vue006dep-xxxxx
# Events:
#   Warning  Unhealthy  Liveness probe failed: Get "http://10.244.0.5:8080/": dial tcp 10.244.0.5:8080: connect: connection refused
#   Normal   Killing    Container vue-container failed liveness probe, will be restarted
```

---

## 실습 명령어

```bash
# 1. 리소스 생성
kubectl apply -f section06/

# 2. Pod 상태 확인
kubectl get pods -l app=vue006kube

# 3. Pod 상세 정보 (Probe 설정 확인)
kubectl describe pod <pod-name>
# Liveness:   http-get http://:80/ delay=5s timeout=1s period=5s #success=1 #failure=1
# Readiness:  http-get http://:80/ delay=3s timeout=1s period=5s #success=1 #failure=1

# 4. Endpoint 확인 (Readiness 통과한 Pod만 표시)
kubectl get endpoints vue006ser

# 5. Probe 실패 시뮬레이션 (포트 변경)
kubectl set image deployment/vue006dep vue-container=limraynor/k8s-vue-ing:broken
# 또는 Probe 포트를 잘못 설정하여 재배포

# 6. 실시간 모니터링
kubectl get pods -w

# 7. 이벤트 확인 (Probe 실패 로그)
kubectl get events --sort-by='.lastTimestamp'

# 8. 리소스 삭제
kubectl delete -f section06/
```

---

## Startup Probe (참고)

Liveness/Readiness 외에 **Startup Probe**도 있습니다:

```yaml
startupProbe:
  httpGet:
    path: /health
    port: 8080
  failureThreshold: 30
  periodSeconds: 10
```

| Probe | 역할 | 언제 실행 |
|---|---|---|
| **Startup** | 앱이 기동 완료되었는지 확인 | 컨테이너 시작 직후 |
| **Liveness** | 앱이 살아있는지 확인 | Startup 성공 이후 |
| **Readiness** | 트래픽 받을 준비 되었는지 확인 | Startup 성공 이후 |

- Startup Probe가 성공할 때까지 Liveness/Readiness는 실행되지 않음
- **기동 시간이 긴 앱** (Java/Spring Boot 등)에 유용
- `initialDelaySeconds`를 길게 설정하는 것보다 정교한 제어 가능

---

## 핵심 개념 정리

### Liveness Probe
- "**살아있나?**" 체크
- 실패 시 → 컨테이너 **재시작**
- 교착 상태, 무한 루프, 메모리 릭으로 응답 불가 시 대응

### Readiness Probe
- "**준비됐나?**" 체크
- 실패 시 → Service에서 **제외** (재시작 X)
- 초기화 중, 과부하 상태에서 트래픽 차단

### failureThreshold
- 연속 실패 **허용 횟수**
- 1이면 1번 실패 즉시 조치 (민감)
- 3이면 3번 연속 실패해야 조치 (관대)

### initialDelaySeconds
- 컨테이너 시작 후 **첫 체크까지 대기 시간**
- 앱 기동 시간을 고려하여 설정
- 너무 짧으면 → 앱 기동 중 Probe 실패 → 불필요한 재시작
- 너무 길면 → 장애 감지가 느려짐

---

## 전체 커리큘럼 흐름 정리 (Section 01 ~ 06)

```
Section 01: 기본 배포 (NodePort)
    └── Deployment로 Pod 생성, NodePort로 외부 접근
    └── 핵심: Pod, Deployment, Service 개념

Section 02: 프로덕션 아키텍처 (ClusterIP + Ingress)
    └── ClusterIP로 내부 통신, Ingress로 URL 라우팅
    └── 핵심: 단일 진입점, 경로 기반 분배

Section 03: 무중단 배포 (Rolling Update)
    └── 서비스 중단 없이 버전 업데이트
    └── 핵심: maxUnavailable, maxSurge, 롤백

Section 04: 영구 저장소 (PV/PVC)
    └── Pod 삭제해도 데이터 유지
    └── 핵심: PV, PVC, emptyDir, volumeMount

Section 05: 설정 외부화 (ConfigMap & Secret)
    └── 코드와 설정 분리, 민감 정보 관리
    └── 핵심: ConfigMap, Secret, 환경변수 주입

Section 06: 헬스 체크 (Probes)  ← 현재
    └── 컨테이너 상태 자동 감시 및 자가 치유
    └── 핵심: Liveness (재시작), Readiness (트래픽 제어)
```

### 학습 완료 후 구축 가능한 아키텍처:

```
외부 요청 → Ingress (경로 라우팅)
               │
        ┌──────┴──────┐
        ▼             ▼
    Vue Service    Boot Service  (ClusterIP)
        │             │
        ▼             ▼
    Vue Pods       Boot Pods
    (Probes)       (Probes)        ← 자동 헬스 체크
    (Rolling)      (Rolling)       ← 무중단 배포
                      │
                      ▼
                   DB Pod
                   (PV/PVC)        ← 영구 저장소
                   (ConfigMap)     ← 설정 외부화
                   (Secret)        ← 비밀번호 관리
```
