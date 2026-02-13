package com.ohgiraffers.payment.listener;

import com.ohgiraffers.payment.dto.OrderMessage;
import com.ohgiraffers.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentListener {

    private final PaymentService paymentService;

    // payment.queue 큐에서 메시지를 수신하면 handle 메소드가 호출됨
    // 메시지는 JSON → OrderMessage 객체로 자동 변환됨
    @RabbitListener(queues = "payment.queue")
    public void handle(OrderMessage order) {
        System.out.println("메시지 수신 - 결제 처리 시작: " + order.orderId());

        // 실제 결제 처리 비즈니스 로직 호출
        paymentService.process(order);
    }
}