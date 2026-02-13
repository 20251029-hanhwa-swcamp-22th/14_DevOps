package com.ohgiraffers.inventory.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit   // RabbitMQ 메시지 리스너 활성화 (ex: @RabbitListener 동작하도록 함)
public class RabbitConfig {

    // inventory.queue 라는 이름의 큐를 정의
    // durable=true 이므로 서버 재시작에도 큐가 유지
    @Bean
    public Queue inventoryQueue() {
        return new Queue("inventory.queue", true);
    }

    // Jackson 기반 JSON 메시지 컨버터를 등록
    // 이 컨버터 덕분에 JSON 형식으로 직렬화된 메시지를 Java 객체로 자동 역직렬화할 수 있음
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // 메시지를 수신하는 컨테이너의 설정 팩토리 정의
    // 메시지를 받을 때 JSON을 Java 객체로 자동 변환하려면 이 설정이 필요함
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);        // RabbitMQ 연결 설정
        factory.setMessageConverter(messageConverter);          // JSON 메시지 컨버터 지정
        return factory;
    }
}