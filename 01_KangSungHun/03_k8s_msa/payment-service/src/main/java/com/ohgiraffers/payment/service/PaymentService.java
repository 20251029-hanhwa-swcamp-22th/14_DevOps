package com.ohgiraffers.payment.service;

import com.ohgiraffers.payment.dto.OrderMessage;
import com.ohgiraffers.payment.entity.Payment;
import com.ohgiraffers.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public void process(OrderMessage order) {
        int pricePerItem = getPrice(order.productId());
        int totalAmount = order.quantity() * pricePerItem;

        Payment payment = Payment.builder()
                .orderId(order.orderId())
                .userId(order.userId())
                .amount(totalAmount)
                .status(totalAmount > 0 ? "PAID" : "FAILED")
                .build();

        paymentRepository.save(payment);
        System.out.println("결제 처리 완료: " + payment.getOrderId() + " / " + payment.getStatus());
    }

    private int getPrice(String productId) {
        // 실제 구현에선 외부 서비스 호출 또는 DB 조회
        return switch (productId) {
            case "product-1" -> 12000;
            case "product-2" -> 30000;
            default -> 10000;
        };
    }
}