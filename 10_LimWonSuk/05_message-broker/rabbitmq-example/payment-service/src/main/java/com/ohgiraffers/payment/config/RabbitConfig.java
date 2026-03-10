package com.ohgiraffers.payment.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit   // RabbitMQ 메시지 리스너(@RabbitListener)를 활성화
public class RabbitConfig {

    // 결제 서비스에서 사용할 큐를 생성
    // durable=true로 설정하면 서버 재시작 시에도 큐가 삭제되지 않음
    @Bean
    public Queue paymentQueue() {
        return new Queue("payment.queue", true);
    }

    // JSON 메시지를 객체로 변환해주는 컨버터를 Bean으로 등록
    // OrderMessage 같은 메시지 객체를 자동으로 변환할 수 있게 도와줌
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // 메시지를 수신하는 리스너 컨테이너의 동작 방식을 설정
    // JSON 메시지를 Java 객체로 변환하려면 messageConverter가 필요
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);  // RabbitMQ 연결 정보
        factory.setMessageConverter(messageConverter);    // JSON 메시지 변환기 설정
        return factory;
    }
}
