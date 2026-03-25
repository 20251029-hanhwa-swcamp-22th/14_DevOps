package com.ohgiraffers.order.service;

import com.ohgiraffers.order.dto.OrderDTO;
import com.ohgiraffers.order.dto.OrderMessage;
import com.ohgiraffers.order.entity.Order;
import com.ohgiraffers.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    public Order createOrder(OrderDTO dto) {

        Order order = Order.builder()
                .userId(dto.getUserId())
                .productId(dto.getProductId())
                .quantity(dto.getQuantity())
                .build();

        Order saved = orderRepository.save(order);
        OrderMessage message = new OrderMessage(
                saved.getOrderId(),
                saved.getProductId(),
                saved.getQuantity(),
                saved.getUserId()
        );
        // 메시지 브로커(RabbitMQ)의 "order.exchange" 익스체인지로 "order.created" 라우팅 키를 통해 message 객체를 전송
        // Jackson2JsonMessageConverter 덕분에 Java 객체가 JSON 문자열로 자동 변환
        rabbitTemplate.convertAndSend("order.exchange", "order.created", message);
        return saved;
    }
}
