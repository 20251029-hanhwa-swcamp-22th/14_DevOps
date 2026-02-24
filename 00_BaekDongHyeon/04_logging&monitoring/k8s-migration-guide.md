# Docker Compose → Kubernetes 마이그레이션 가이드

## 목차
1. [개념 매핑](#1-개념-매핑-docker-compose--kubernetes)
2. [전체 구조](#2-전체-k8s-구조)
3. [사전 준비](#3-사전-준비)
4. [Step 1 - Secret (민감정보)](#step-1-secret-민감정보-관리)
5. [Step 2 - ConfigMap (설정 파일)](#step-2-configmap-설정-파일-관리)
6. [Step 3 - PersistentVolumeClaim (데이터 영속성)](#step-3-persistentvolumeclaim-데이터-영속성)
7. [Step 4 - 각 서비스 Deployment + Service](#step-4-각-서비스-deployment--service)
8. [Step 5 - 적용 및 확인](#step-5-적용-및-확인)
9. [Docker Compose vs Kubernetes 핵심 차이](#docker-compose-vs-kubernetes-핵심-차이)

---

## 1. 개념 매핑: Docker Compose → Kubernetes

| Docker Compose | Kubernetes | 설명 |
|---|---|---|
| `service` | `Deployment` + `Pod` | 컨테이너 실행 단위 |
| `ports` | `Service` | 네트워크 노출 |
| `environment` | `Secret` / `ConfigMap` | 환경변수 주입 |
| `volumes` (named) | `PersistentVolumeClaim` | 데이터 영속성 |
| `volumes` (bind mount) | `ConfigMap` | 설정 파일 마운트 |
| `depends_on` | `initContainers` / 재시작 정책 | 실행 순서 |
| 서비스 이름 (hostname) | `Service` 이름 | 내부 DNS |

### 핵심 차이: 내부 통신 방식

```
# Docker Compose
# 서비스 이름이 곧 hostname
hosts => ["elasticsearch:9200"]  ← 서비스 이름으로 바로 통신

# Kubernetes
# Service 리소스를 만들어야 hostname이 생김
hosts => ["elasticsearch-service:9200"]  ← Service 이름으로 통신
```

---

## 2. 전체 K8s 구조

```
k8s/
├── secret.yml                  # DB 비밀번호 등 민감정보
├── configmap-logstash.yml      # logstash.conf
├── configmap-prometheus.yml    # prometheus.yml
├── configmap-grafana.yml       # datasource.yml
├── pvc.yml                     # PersistentVolumeClaim (db, es, grafana)
├── mariadb.yml                 # Deployment + Service
├── elasticsearch.yml           # Deployment + Service
├── logstash.yml                # Deployment + Service
├── kibana.yml                  # Deployment + Service
├── prometheus.yml              # Deployment + Service
├── grafana.yml                 # Deployment + Service
└── order-service.yml           # Deployment + Service
```

---

## 3. 사전 준비

### order-service 이미지를 레지스트리에 푸시

K8s는 로컬 빌드 이미지를 직접 쓸 수 없으므로, Docker Hub 등에 푸시해야 합니다.

```bash
# 이미지 빌드
docker build -t <도커허브ID>/order-service:latest ./order-service

# Docker Hub에 푸시
docker push <도커허브ID>/order-service:latest
```

### kubectl 설치 확인

```bash
kubectl version --client
kubectl get nodes   # 클러스터 연결 확인
```

### 네임스페이스 생성 (선택사항)

```bash
kubectl create namespace monitoring
```

---

## Step 1: Secret (민감정보 관리)

docker-compose.yml의 `environment` 중 비밀번호를 Secret으로 분리합니다.

```yaml
# k8s/secret.yml
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
type: Opaque
stringData:                          # stringData는 평문 입력 → k8s가 자동으로 base64 인코딩
  MARIADB_ROOT_PASSWORD: root
  SPRING_DATASOURCE_USERNAME: root
  SPRING_DATASOURCE_PASSWORD: root
```

> **참고**: 실제 운영에서는 `stringData`에 평문을 쓰지 않고,
> `kubectl create secret generic` 명령어 또는 외부 Secret 관리 도구(Vault 등)를 사용합니다.

---

## Step 2: ConfigMap (설정 파일 관리)

docker-compose의 **bind mount** (`./docker/logstash/logstash.conf:...`)를
ConfigMap으로 대체합니다.

### Logstash 설정

```yaml
# k8s/configmap-logstash.yml
apiVersion: v1
kind: ConfigMap
metadata:
  name: logstash-config
data:
  logstash.conf: |
    input {
      tcp {
        port => 5000
        codec => json
      }
    }
    output {
      elasticsearch {
        hosts => ["elasticsearch-service:9200"]   # ← Service 이름으로 변경
        index => "order-service-%{+YYYY.MM.dd}"
      }
    }
```

### Prometheus 설정

```yaml
# k8s/configmap-prometheus.yml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
    scrape_configs:
      - job_name: 'spring-boot'
        metrics_path: '/actuator/prometheus'
        static_configs:
          - targets: ['order-service:8080']       # ← Service 이름으로 변경
```

### Grafana 데이터소스 설정

```yaml
# k8s/configmap-grafana.yml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-config
data:
  datasource.yml: |
    apiVersion: 1
    datasources:
      - name: Prometheus
        type: prometheus
        access: proxy
        url: http://prometheus-service:9090       # ← Service 이름으로 변경
        isDefault: true
```

---

## Step 3: PersistentVolumeClaim (데이터 영속성)

docker-compose의 **named volume** (`db_data`, `es_data`, `grafana_data`)을
PVC로 대체합니다.

```yaml
# k8s/pvc.yml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: db-pvc
spec:
  accessModes:
    - ReadWriteOnce      # 하나의 노드에서 읽기/쓰기
  resources:
    requests:
      storage: 1Gi
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: es-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: grafana-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 500Mi
```

---

## Step 4: 각 서비스 Deployment + Service

### MariaDB

```yaml
# k8s/mariadb.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mariadb
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mariadb
  template:
    metadata:
      labels:
        app: mariadb
    spec:
      containers:
        - name: mariadb
          image: mariadb:10.7
          env:
            - name: MARIADB_ROOT_PASSWORD
              valueFrom:
                secretKeyRef:               # Secret에서 값 가져오기
                  name: db-secret
                  key: MARIADB_ROOT_PASSWORD
            - name: MARIADB_DATABASE
              value: orders
          ports:
            - containerPort: 3306
          volumeMounts:
            - name: db-storage
              mountPath: /var/lib/mysql
      volumes:
        - name: db-storage
          persistentVolumeClaim:
            claimName: db-pvc             # PVC 연결
---
apiVersion: v1
kind: Service
metadata:
  name: mariadb-service
spec:
  selector:
    app: mariadb
  ports:
    - port: 3306
      targetPort: 3306
  type: ClusterIP                         # 클러스터 내부에서만 접근
```

### Elasticsearch

```yaml
# k8s/elasticsearch.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: elasticsearch
spec:
  replicas: 1
  selector:
    matchLabels:
      app: elasticsearch
  template:
    metadata:
      labels:
        app: elasticsearch
    spec:
      containers:
        - name: elasticsearch
          image: docker.elastic.co/elasticsearch/elasticsearch:7.17.0
          env:
            - name: discovery.type
              value: single-node
            - name: ES_JAVA_OPTS
              value: "-Xms512m -Xmx512m"
          ports:
            - containerPort: 9200
          volumeMounts:
            - name: es-storage
              mountPath: /usr/share/elasticsearch/data
      volumes:
        - name: es-storage
          persistentVolumeClaim:
            claimName: es-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: elasticsearch-service
spec:
  selector:
    app: elasticsearch
  ports:
    - port: 9200
      targetPort: 9200
  type: ClusterIP
```

### Logstash

```yaml
# k8s/logstash.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: logstash
spec:
  replicas: 1
  selector:
    matchLabels:
      app: logstash
  template:
    metadata:
      labels:
        app: logstash
    spec:
      containers:
        - name: logstash
          image: docker.elastic.co/logstash/logstash:7.17.0
          ports:
            - containerPort: 5000
          volumeMounts:
            - name: logstash-config
              mountPath: /usr/share/logstash/pipeline/logstash.conf
              subPath: logstash.conf      # ConfigMap의 특정 키를 파일로 마운트
      volumes:
        - name: logstash-config
          configMap:
            name: logstash-config         # ConfigMap 연결
---
apiVersion: v1
kind: Service
metadata:
  name: logstash-service
spec:
  selector:
    app: logstash
  ports:
    - port: 5000
      targetPort: 5000
  type: ClusterIP
```

### Kibana

```yaml
# k8s/kibana.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kibana
spec:
  replicas: 1
  selector:
    matchLabels:
      app: kibana
  template:
    metadata:
      labels:
        app: kibana
    spec:
      containers:
        - name: kibana
          image: docker.elastic.co/kibana/kibana:7.17.0
          env:
            - name: ELASTICSEARCH_HOSTS
              value: http://elasticsearch-service:9200
          ports:
            - containerPort: 5601
---
apiVersion: v1
kind: Service
metadata:
  name: kibana-service
spec:
  selector:
    app: kibana
  ports:
    - port: 5601
      targetPort: 5601
  type: NodePort                          # 외부 브라우저 접속용
  # NodePort: 클러스터 노드의 랜덤 포트(30000~32767)로 외부 노출
```

### Prometheus

```yaml
# k8s/prometheus.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      containers:
        - name: prometheus
          image: prom/prometheus:latest
          ports:
            - containerPort: 9090
          volumeMounts:
            - name: prometheus-config
              mountPath: /etc/prometheus/prometheus.yml
              subPath: prometheus.yml
      volumes:
        - name: prometheus-config
          configMap:
            name: prometheus-config
---
apiVersion: v1
kind: Service
metadata:
  name: prometheus-service
spec:
  selector:
    app: prometheus
  ports:
    - port: 9090
      targetPort: 9090
  type: ClusterIP
```

### Grafana

```yaml
# k8s/grafana.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
spec:
  replicas: 1
  selector:
    matchLabels:
      app: grafana
  template:
    metadata:
      labels:
        app: grafana
    spec:
      containers:
        - name: grafana
          image: grafana/grafana:9.4.0
          ports:
            - containerPort: 3000
          volumeMounts:
            - name: grafana-storage
              mountPath: /var/lib/grafana
            - name: grafana-config
              mountPath: /etc/grafana/provisioning/datasources/datasource.yml
              subPath: datasource.yml
      volumes:
        - name: grafana-storage
          persistentVolumeClaim:
            claimName: grafana-pvc
        - name: grafana-config
          configMap:
            name: grafana-config
---
apiVersion: v1
kind: Service
metadata:
  name: grafana-service
spec:
  selector:
    app: grafana
  ports:
    - port: 3000
      targetPort: 3000
  type: NodePort                          # 외부 브라우저 접속용
```

### order-service

```yaml
# k8s/order-service.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 1
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
        - name: order-service
          image: <도커허브ID>/order-service:latest   # ← 본인 이미지로 교체
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_DATASOURCE_URL
              value: jdbc:mariadb://mariadb-service:3306/orders?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
            - name: SPRING_DATASOURCE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: db-secret
                  key: SPRING_DATASOURCE_USERNAME
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: db-secret
                  key: SPRING_DATASOURCE_PASSWORD
---
apiVersion: v1
kind: Service
metadata:
  name: order-service
spec:
  selector:
    app: order-service
  ports:
    - port: 8080
      targetPort: 8080
  type: NodePort                          # 외부 API 호출용
```

---

## Step 5: 적용 및 확인

### 순서대로 적용

```bash
# 1. 민감정보 먼저
kubectl apply -f k8s/secret.yml

# 2. 설정 파일
kubectl apply -f k8s/configmap-logstash.yml
kubectl apply -f k8s/configmap-prometheus.yml
kubectl apply -f k8s/configmap-grafana.yml

# 3. 볼륨
kubectl apply -f k8s/pvc.yml

# 4. 인프라 서비스 (의존성 순서)
kubectl apply -f k8s/mariadb.yml
kubectl apply -f k8s/elasticsearch.yml
kubectl apply -f k8s/logstash.yml
kubectl apply -f k8s/kibana.yml
kubectl apply -f k8s/prometheus.yml
kubectl apply -f k8s/grafana.yml

# 5. 애플리케이션
kubectl apply -f k8s/order-service.yml
```

또는 디렉토리 일괄 적용:

```bash
kubectl apply -f k8s/
```

### 상태 확인

```bash
# Pod 상태 확인
kubectl get pods

# Service 확인 (NodePort 번호 확인)
kubectl get services

# Pod 로그 확인
kubectl logs -f <pod-name>

# Pod 상세 정보 (문제 발생 시)
kubectl describe pod <pod-name>
```

### 브라우저 접속

NodePort 서비스는 `kubectl get services`에서 확인한 포트로 접속합니다.

```
# 예시 (NodePort 번호는 실행 후 확인)
Kibana:  http://localhost:<NodePort>
Grafana: http://localhost:<NodePort>
```

---

## Docker Compose vs Kubernetes 핵심 차이

| 항목 | Docker Compose | Kubernetes |
|---|---|---|
| **hostname** | 서비스 이름 자동 = hostname | Service 리소스를 별도 생성해야 함 |
| **설정 파일** | bind mount로 로컬 파일 직접 연결 | ConfigMap으로 변환 후 마운트 |
| **비밀번호** | environment에 평문 작성 | Secret으로 분리 |
| **데이터 영속성** | named volume | PersistentVolumeClaim |
| **외부 접속** | ports로 호스트 포트 매핑 | NodePort / LoadBalancer / Ingress |
| **이미지** | 로컬 빌드 이미지 사용 가능 | 레지스트리(Docker Hub 등) 필요 |
| **실행 순서** | depends_on | initContainers 또는 재시작 정책 |