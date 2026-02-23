package com.ohgiraffers.inventory.listener;

import com.ohgiraffers.inventory.dto.OrderMessage;
import com.ohgiraffers.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryListener {

    private final InventoryService inventoryService;

    // inventory.queue 큐에 메시지가 오면 이 메소드가 호출됨
    // 메시지는 JSON -> OrderMessage 객체로 자동 변환됨 (Jackson 컨버터)
    @RabbitListener(queues = "inventory.queue")
    public void handle(OrderMessage order) {
        System.out.println("메시지 수신 - 재고 처리 시작: " + order.orderId());

        // 실제 재고 처리 로직 호출
        inventoryService.reserve(order);
    }
}