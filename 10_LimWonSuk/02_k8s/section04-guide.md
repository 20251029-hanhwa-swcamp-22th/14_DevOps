# Section 04 — Persistent Volume (영구 저장소)

## 이 섹션에서 배우는 것

- Pod가 삭제되어도 **데이터가 유지**되는 영구 저장소 개념
- **PersistentVolume (PV)** — 클러스터 레벨의 저장소 리소스
- **PersistentVolumeClaim (PVC)** — Pod가 저장소를 요청하는 방법
- **emptyDir** — 같은 Pod 내 컨테이너 간 임시 데이터 공유

---

## 왜 영구 저장소가 필요한가?

### Pod의 기본 특성
```
Pod 생성 → 데이터 쓰기 → Pod 삭제 → 데이터 사라짐! 💀
```

- Pod 내부의 파일 시스템은 **임시(Ephemeral)**
- Pod가 재시작되거나 삭제되면 내부 데이터도 함께 삭제
- **데이터베이스**를 Pod에서 실행하면? → Pod 재시작 시 모든 데이터 손실!

### 해결: 외부 저장소에 데이터 보관
```
Pod 생성 → 외부 저장소에 쓰기 → Pod 삭제 → 데이터 유지! ✅
                                  Pod 재생성 → 외부 저장소 다시 연결 → 데이터 그대로!
```

---

## 전체 구조도

### PV/PVC를 사용한 MySQL 구조

```
┌──────────────────────────────────────────────────────┐
│                  Kubernetes Cluster                   │
│                                                      │
│  ┌──────────────────┐                                │
│  │   Pod (mysql)    │                                │
│  │                  │                                │
│  │  ┌────────────┐  │     ┌─────────────────┐       │
│  │  │  MySQL 8.0 │  │     │    PVC           │       │
│  │  │            │──┼────→│    pvc-mysql     │       │
│  │  │ /var/lib/  │  │     │    요청: 2Gi     │       │
│  │  │   mysql    │  │     └────────┬────────┘       │
│  │  └────────────┘  │              │ 바인딩          │
│  └──────────────────┘              │                 │
│                                    ▼                 │
│                          ┌─────────────────┐         │
│                          │    PV            │         │
│                          │    pv-mysql      │         │
│                          │    용량: 2Gi     │         │
│                          │                  │         │
│                          │  hostPath:       │         │
│                          │  /mnt/mysql-data │         │
│                          └────────┬─────────┘         │
│                                   │                   │
└───────────────────────────────────┼───────────────────┘
                                    │
                                    ▼
                          ┌─────────────────┐
                          │  워커 노드 디스크  │
                          │  /mnt/mysql-data │
                          │  (실제 데이터)    │
                          └─────────────────┘
```

---

## 파일 목록 및 역할

| 파일 | 종류 | 역할 |
|---|---|---|
| `pv-mysql.yaml` | PersistentVolume | 클러스터에 2Gi 저장소 생성 |
| `pvc-mysql.yaml` | PersistentVolumeClaim | Pod가 저장소를 요청 |
| `mysql.yaml` | Pod | MySQL 컨테이너 + 볼륨 마운트 |
| `log-collector.yaml` | Pod | 멀티 컨테이너 + emptyDir 예제 |

---

## 파일별 상세 설명

### 1. `pv-mysql.yaml` — PersistentVolume (저장소 생성)

```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: pv-mysql
spec:
  capacity:
    storage: 2Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: "/mnt/mysql-data"
```

**각 필드 설명:**

| 필드 | 값 | 설명 |
|---|---|---|
| `kind` | PersistentVolume | **클러스터 레벨** 리소스 (네임스페이스에 속하지 않음) |
| `capacity.storage` | 2Gi | 이 볼륨의 총 용량 (2 기가바이트) |
| `accessModes` | ReadWriteOnce | 한 번에 하나의 노드에서만 읽기/쓰기 가능 |
| `hostPath.path` | /mnt/mysql-data | 워커 노드의 실제 디렉토리 경로 |

**Access Modes 종류:**

| 모드 | 약어 | 설명 | 사용 예 |
|---|---|---|---|
| `ReadWriteOnce` | RWO | 단일 노드에서 읽기/쓰기 | MySQL, PostgreSQL 등 단일 DB |
| `ReadOnlyMany` | ROX | 여러 노드에서 읽기 전용 | 설정 파일, 정적 콘텐츠 공유 |
| `ReadWriteMany` | RWX | 여러 노드에서 읽기/쓰기 | 공유 파일 시스템 (NFS 등) |

> **hostPath 주의사항:** 개발/테스트용으로만 사용! 프로덕션에서는 NFS, AWS EBS, GCP PD 등을 사용해야 합니다.

---

### 2. `pvc-mysql.yaml` — PersistentVolumeClaim (저장소 요청)

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pvc-mysql
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
```

**PV와 PVC의 관계:**

```
PV (관리자가 생성)              PVC (개발자가 요청)
┌──────────────────┐          ┌──────────────────┐
│ name: pv-mysql   │    바인딩  │ name: pvc-mysql  │
│ capacity: 2Gi    │◄────────►│ requests: 2Gi    │
│ accessModes: RWO │          │ accessModes: RWO │
└──────────────────┘          └──────────────────┘
```

**바인딩 조건:**
1. PVC의 `requests.storage` ≤ PV의 `capacity.storage`
2. PVC의 `accessModes`가 PV의 `accessModes`에 포함
3. 조건을 만족하는 PV가 있으면 **자동으로 바인딩**

> **비유:** PV는 "아파트" (관리자가 건설), PVC는 "입주 신청서" (개발자가 제출)

---

### 3. `mysql.yaml` — MySQL Pod (볼륨 사용)

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: mysql
spec:
  containers:
    - name: mysql
      image: mysql:8.0
      env:
        - name: MYSQL_ROOT_PASSWORD
          value: "root1234"
      volumeMounts:
        - name: mysql-storage
          mountPath: /var/lib/mysql
  volumes:
    - name: mysql-storage
      persistentVolumeClaim:
        claimName: pvc-mysql
```

**데이터 흐름:**

```
MySQL 컨테이너
    │
    │ 데이터를 /var/lib/mysql 에 저장
    │ (MySQL의 기본 데이터 디렉토리)
    ▼
volumeMount (mysql-storage)
    │
    │ mysql-storage라는 이름의 볼륨에 매핑
    ▼
volume (persistentVolumeClaim: pvc-mysql)
    │
    │ pvc-mysql이 pv-mysql에 바인딩됨
    ▼
PV (pv-mysql)
    │
    │ hostPath: /mnt/mysql-data
    ▼
워커 노드의 /mnt/mysql-data 디렉토리에 실제 저장!
```

**환경 변수:**
- `MYSQL_ROOT_PASSWORD`: MySQL root 계정 비밀번호
- MySQL 공식 이미지에서 **필수로 설정해야 하는 환경 변수**

**데이터 영속성 테스트:**
```bash
# 1. MySQL에 접속하여 데이터 생성
kubectl exec -it mysql -- mysql -u root -proot1234
# CREATE DATABASE testdb;
# USE testdb;
# CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50));
# INSERT INTO users VALUES (1, 'Kim');

# 2. Pod 삭제
kubectl delete pod mysql

# 3. Pod 재생성
kubectl apply -f section04/mysql.yaml

# 4. 데이터 확인 → 여전히 존재!
kubectl exec -it mysql -- mysql -u root -proot1234 -e "SELECT * FROM testdb.users;"
# +----+------+
# | id | name |
# +----+------+
# |  1 | Kim  |
# +----+------+
```

---

### 4. `log-collector.yaml` — 멀티 컨테이너 + emptyDir

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: log-collector
spec:
  containers:
    # 컨테이너 1: 로그 작성자
    - name: web-app
      image: busybox
      command: ["sh", "-c", "while true; do echo $(date) Hello from web app >> /var/log/webapp.log; sleep 5; done"]
      volumeMounts:
        - name: logs
          mountPath: /var/log

    # 컨테이너 2: 로그 읽기자
    - name: log-monitor
      image: busybox
      command: ["sh", "-c", "tail -f /var/log/webapp.log"]
      volumeMounts:
        - name: logs
          mountPath: /var/log

  volumes:
    - name: logs
      emptyDir: {}
```

**구조도:**

```
┌─────────────────── Pod (log-collector) ───────────────────┐
│                                                           │
│  ┌─────────────────┐          ┌─────────────────┐        │
│  │ Container 1     │          │ Container 2     │        │
│  │ web-app         │          │ log-monitor     │        │
│  │                 │          │                 │        │
│  │ 매 5초마다      │          │ tail -f로       │        │
│  │ 로그 작성       │          │ 실시간 로그 읽기 │        │
│  │                 │          │                 │        │
│  │ /var/log ──┐    │          │    ┌── /var/log │        │
│  └────────────┼────┘          └────┼────────────┘        │
│               │                    │                     │
│               ▼                    ▼                     │
│          ┌────────────────────────────────┐              │
│          │     emptyDir (logs)            │              │
│          │     webapp.log                 │              │
│          │     (Pod 내 공유 임시 저장소)     │              │
│          └────────────────────────────────┘              │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

**emptyDir vs PV/PVC:**

| 항목 | emptyDir | PV/PVC |
|---|---|---|
| **수명** | Pod와 동일 (Pod 삭제 시 사라짐) | Pod와 독립 (Pod 삭제해도 유지) |
| **용도** | 컨테이너 간 임시 데이터 공유 | 영구적 데이터 저장 |
| **설정** | 간단 (`emptyDir: {}`) | PV 생성 + PVC 생성 + 마운트 |
| **사용 예** | 로그 공유, 캐시, 임시 파일 | DB, 파일 업로드, 설정 파일 |

**실습 명령어:**

```bash
# Pod 생성
kubectl apply -f section04/log-collector.yaml

# 로그 작성 컨테이너 확인
kubectl logs log-collector -c web-app

# 로그 읽기 컨테이너 확인 (실시간 로그 출력!)
kubectl logs log-collector -c log-monitor -f
# Thu Jan 01 00:00:00 UTC 2025 Hello from web app
# Thu Jan 01 00:00:05 UTC 2025 Hello from web app
# Thu Jan 01 00:00:10 UTC 2025 Hello from web app
# ... (5초마다 새 로그)
```

---

## 실습 명령어 종합

```bash
# 1. PV 생성 (클러스터 저장소 준비)
kubectl apply -f section04/pv-mysql.yaml

# 2. PVC 생성 (저장소 요청)
kubectl apply -f section04/pvc-mysql.yaml

# 3. PV-PVC 바인딩 확인
kubectl get pv
# NAME       CAPACITY   ACCESS MODES   STATUS   CLAIM
# pv-mysql   2Gi        RWO            Bound    default/pvc-mysql

kubectl get pvc
# NAME        STATUS   VOLUME     CAPACITY   ACCESS MODES
# pvc-mysql   Bound    pv-mysql   2Gi        RWO

# 4. MySQL Pod 생성
kubectl apply -f section04/mysql.yaml

# 5. MySQL 접속 테스트
kubectl exec -it mysql -- mysql -u root -proot1234

# 6. log-collector Pod 생성
kubectl apply -f section04/log-collector.yaml

# 7. 리소스 삭제 (순서 주의!)
kubectl delete pod mysql
kubectl delete pod log-collector
kubectl delete pvc pvc-mysql    # PVC 먼저 삭제
kubectl delete pv pv-mysql      # PV 나중에 삭제
```

---

## PV/PVC 생명주기

```
1. Provisioning (프로비저닝)
   └── 관리자가 PV 생성 (또는 StorageClass로 동적 생성)

2. Binding (바인딩)
   └── PVC 생성 시 조건에 맞는 PV와 자동 바인딩
   └── 1:1 관계 (하나의 PV는 하나의 PVC에만 바인딩)

3. Using (사용)
   └── Pod가 PVC를 volumeMount로 사용

4. Releasing (해제)
   └── PVC 삭제 시 PV가 Released 상태로 전환

5. Reclaiming (회수)
   └── Retain: 데이터 유지 (수동 삭제 필요)
   └── Delete: PV와 데이터 함께 삭제
   └── Recycle: 데이터 삭제 후 PV 재사용 (deprecated)
```

---

## 핵심 개념 정리

### PersistentVolume (PV)
- **클러스터 레벨** 리소스 (네임스페이스에 속하지 않음)
- 관리자가 미리 생성해두는 **실제 저장소**
- 물리적 저장소의 종류를 추상화 (hostPath, NFS, AWS EBS 등)

### PersistentVolumeClaim (PVC)
- **네임스페이스 레벨** 리소스
- 개발자가 "이 만큼의 저장소가 필요합니다"라고 **요청**하는 것
- 조건에 맞는 PV에 자동 바인딩

### emptyDir
- Pod 내 컨테이너 간 **임시 공유 볼륨**
- Pod 삭제 시 함께 삭제됨
- 사이드카(Sidecar) 패턴에서 많이 사용

### volumeMount
- 컨테이너 내부의 **특정 경로**에 볼륨을 연결
- `mountPath`: 컨테이너 내부 경로
- `name`: volumes에 정의된 볼륨 이름과 매칭

---

## Section 01 → 02 → 03 → 04 흐름 정리

```
Section 01: 기본 배포 (NodePort)
Section 02: 프로덕션 아키텍처 (ClusterIP + Ingress)
Section 03: 무중단 배포 (Rolling Update)
Section 04: 영구 저장소 (PV/PVC)  ← 현재
    └── Pod가 삭제되어도 데이터 유지
    └── DB를 K8s에서 안전하게 운영 가능
    └── 다음: 설정값을 외부에서 주입하기 (Section 05)
```
