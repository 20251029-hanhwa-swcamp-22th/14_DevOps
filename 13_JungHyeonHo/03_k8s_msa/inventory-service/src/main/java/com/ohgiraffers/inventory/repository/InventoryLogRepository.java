package com.ohgiraffers.inventory.repository;

import com.ohgiraffers.inventory.entity.InventoryLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLogRepository extends JpaRepository<InventoryLog, Long> {
}
