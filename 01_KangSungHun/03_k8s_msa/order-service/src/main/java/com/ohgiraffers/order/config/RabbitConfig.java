package com.ohgiraffers.order.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // 교환기(Exchange) 이름 상수
    // Exchange는 들어오는 메시지를 하나 이상의 Queue로 라우팅하는 역할을 함
    public static final String ORDER_EXCHANGE = "order.exchange";

    // 각 서비스가 소비할 큐 이름 상수
    // Queue는 메시지를 저장하고, 소비자(Consumer)가 읽어가는 버퍼 역할을 함
    public static final String PAYMENT_QUEUE = "payment.queue";
    public static final String INVENTORY_QUEUE = "inventory.queue";

    // 라우팅 키 이름
    // routing key는 메시지가 어떤 Queue로 가야 하는지 결정하는 식별자 역할
    public static final String ROUTING_KEY = "order.created";

    /* TopicExchange 빈 등록
     * - TopicExchange는 라우팅 키 패턴 매칭 기능을 제공하는 Exchange 타입
     * - 와일드카드(*)와 해시(#)를 사용해 다양한 패턴 기반 라우팅이 가능
     *   예: "order.*.created", "order.#" 등
     */
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    /* 결제 서비스용 Queue 빈 등록
     * - Queue는 수신된 메시지를 저장하는 버퍼
     * - Exchange로부터 바인딩된 routing key에 따라 메시지를 가져감
     */
    @Bean
    public Queue paymentQueue() {
        return new Queue(PAYMENT_QUEUE);
    }

    /* 재고 서비스용 Queue 빈 등록
     */
    @Bean
    public Queue inventoryQueue() {
        return new Queue(INVENTORY_QUEUE);
    }

    /* 결제 큐와 Exchange를 바인딩
     * - Binding은 Queue와 Exchange를 연결하고, 어떤 routing key에 반응할지 정의
     * - 여기서는 "order.created" 키가 달린 메시지를 paymentQueue로 전달
     */
    @Bean
    public Binding paymentBinding() {
        return BindingBuilder
                .bind(paymentQueue())      // 결제 큐 지정
                .to(exchange())            // TopicExchange 지정
                .with(ROUTING_KEY);        // 매칭할 routing key
    }

    /* 재고 큐와 Exchange를 바인딩
     * - 동일한 routing key를 사용하면 하나의 메시지가 여러 큐로 동시에 전달될 수 있음
     */
    @Bean
    public Binding inventoryBinding() {
        return BindingBuilder
                .bind(inventoryQueue())
                .to(exchange())
                .with(ROUTING_KEY);
    }

    /* 메시지 컨버터 빈 등록
     * - Jackson2JsonMessageConverter를 사용해 Java 객체 ↔ JSON 문자열로 자동 변환
     * - 기본 SimpleMessageConverter 대신 사용하면 메시지 가독성 및 이식성 향상
     */
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /* RabbitTemplate 빈 등록
     * - 메시지를 전송할 때 사용하는 핵심 템플릿
     * - ConnectionFactory와 JSON 컨버터를 주입하여 객체 직렬화/역직렬화를 자동 처리
     */
    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter
    ) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}