package com.ohgiraffers.inventory.service;

import com.ohgiraffers.inventory.dto.OrderMessage;
import com.ohgiraffers.inventory.entity.InventoryLog;
import com.ohgiraffers.inventory.repository.InventoryLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryLogRepository repository;

    public void reserve(OrderMessage order) {
        // 여기선 항상 성공 처리하지만 실제로는 상품 서비스에서 재고 수량 체크해야 함
        InventoryLog log = InventoryLog.builder()
                .orderId(order.orderId())
                .productId(order.productId())
                .quantity(order.quantity())
                .status("RESERVED")
                .build();

        repository.save(log);
        System.out.println("재고 처리 완료: " + log.getOrderId() + " / " + log.getStatus());
    }
}