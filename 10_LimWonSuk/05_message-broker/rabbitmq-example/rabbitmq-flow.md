# RabbitMQ 주문 메시지 흐름

## 1. 주문 생성 (Order Service)

`OrderService.createOrder()` 에서:

1. DTO → Entity 변환 후 DB에 저장
2. 저장된 주문 정보로 `OrderMessage` 객체 생성
3. `rabbitTemplate.convertAndSend("order.exchange", "order.created", message)` 호출
   - **exchange**: `order.exchange` (TopicExchange)
   - **routing key**: `order.created`
   - Jackson 컨버터가 Java 객체 → JSON으로 자동 변환

## 2. Exchange가 메시지를 라우팅 (RabbitMQ)

`RabbitConfig`에서 **같은 routing key** `order.created`로 **2개의 큐**가 바인딩되어 있음:

```
order.exchange ──(order.created)──→ payment.queue
               ──(order.created)──→ inventory.queue
```

즉, **메시지 1개를 보내면 2개의 큐에 동시에 복사**됩니다 (Fan-out 패턴과 유사).

## 3. 각 서비스가 메시지 수신

| 서비스 | 리스너 | 큐 | 처리 |
|--------|--------|-----|------|
| **Payment** | `PaymentListener.handle()` | `payment.queue` | `paymentService.process(order)` → 결제 처리 |
| **Inventory** | `InventoryListener.handle()` | `inventory.queue` | `inventoryService.reserve(order)` → 재고 차감 |

두 리스너 모두 `@RabbitListener`로 각자의 큐를 구독하고 있어서, 메시지가 도착하면 자동으로 JSON → `OrderMessage` 변환 후 처리합니다.

## 요약 그림

```
[Client] → POST /order → [OrderService]
                              │
                         DB 저장 + 메시지 발행
                              │
                              ▼
                      [order.exchange]
                       (TopicExchange)
                        /          \
            order.created      order.created
                /                    \
               ▼                      ▼
        [payment.queue]        [inventory.queue]
               │                      │
               ▼                      ▼
        PaymentListener        InventoryListener
               │                      │
               ▼                      ▼
        결제 처리(process)      재고 차감(reserve)
```

## 핵심 포인트

- Order Service가 Payment/Inventory를 **직접 호출하지 않고**, RabbitMQ를 통해 메시지만 발행
- 각 서비스가 **독립적으로 비동기 수신/처리**
- 서비스 간 **결합도가 낮아지고**, 한쪽이 다운되어도 큐에 메시지가 보관됨

---

## RabbitMQ vs Kafka 비교

### 일반 비교

| 항목 | RabbitMQ | Kafka |
|------|----------|-------|
| **기반 프로토콜** | AMQP (Advanced Message Queuing Protocol) | 자체 설계 프로토콜 (Kafka Protocol) |
| **전송 방식** | Push: 브로커가 컨슈머에게 메시지를 밀어줌 | Pull: 컨슈머가 브로커로부터 직접 메시지를 가져감 |
| **저장 구조** | 메시지를 큐에 저장, 소비 후 삭제 (durable 설정 가능) | 모든 메시지를 디스크 로그에 저장 (append-only), 오프셋 기반 읽기 |
| **주요 장점** | 다양한 라우팅, 낮은 지연(latency), 경량 구조, 설정 유연 | 대용량 처리, 확장성, 장애 복구 용이, 스트리밍 처리에 특화 |
| **적합한 용도** | 결제 흐름, 실시간 알림, MSA 간 단일 이벤트 흐름 | 로그 수집, 실시간 분석, IoT 데이터, 빅데이터 파이프라인 |
| **내결함성** | ACK/NACK 기반 메시지 확인, 재시도 등 신뢰성 높음 | 디스크에 메시지가 남아 복구와 재처리에 강함 |

- RabbitMQ는 빠른 응답과 복잡한 라우팅이 필요한 시스템에 적합
- Kafka는 많은 양의 데이터를 안정적으로 저장하고 처리하는 데 최적화

### 이 프로젝트에서의 비교

| 항목 | rabbitmq-example (주문 시스템) | kafka-example (채팅 앱) |
|------|-------------------------------|------------------------|
| **용도** | 주문 → 결제/재고 처리 (비즈니스 이벤트) | 실시간 채팅 메시지 브로드캐스트 |
| **메시지 발행** | `RabbitTemplate.convertAndSend()` | `KafkaTemplate.send()` |
| **메시지 수신** | `@RabbitListener(queues = "...")` | `@KafkaListener(topics = "...")` |
| **라우팅 방식** | Exchange + Routing Key로 여러 큐에 분배 | Topic 이름(`chat-topic-{roomId}`)으로 분리 |
| **컨슈머 구조** | 서비스별 독립 큐 (payment.queue, inventory.queue) | Consumer Group으로 메시지 분산 처리 |
| **메시지 처리 후** | 큐에서 삭제됨 | 디스크 로그에 보존 (오프셋으로 재읽기 가능) |
| **설정 클래스** | `RabbitConfig` - Exchange, Queue, Binding 선언 | `KafkaConfig` - ProducerFactory, ConsumerFactory 설정 |
| **직렬화** | `Jackson2JsonMessageConverter` (Spring AMQP) | `JsonSerializer` / `JsonDeserializer` (Spring Kafka) |

### 프로젝트별 흐름 비교

**RabbitMQ (주문 시스템) - Push 방식**
```
Client → OrderController → OrderService
                              │
                        RabbitTemplate.convertAndSend()
                              │
                              ▼
                      [order.exchange] ─── Push ──→ [payment.queue] → PaymentListener
                                       ─── Push ──→ [inventory.queue] → InventoryListener
```
- Exchange가 라우팅 키 기반으로 **브로커가 메시지를 밀어줌(Push)**
- 1개의 메시지가 2개의 큐로 **동시 분배**

**Kafka (채팅 앱) - Pull 방식**
```
Client → WebSocket(/app/chat.send) → ChatSocketController
                                          │
                                    KafkaTemplate.send("chat-topic-room1", message)
                                          │
                                          ▼
                                  [chat-topic-room1] ← Pull ── KafkaChatListener
                                                                      │
                                                              SimpMessagingTemplate
                                                                      │
                                                                      ▼
                                                        WebSocket(/topic/room/room1) → Client
```
- 컨슈머가 토픽에서 **직접 메시지를 가져감(Pull)**
- 메시지는 디스크에 **보존**되어 나중에 다시 읽기 가능
