package com.ohgiraffers.order.repository;

import com.ohgiraffers.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {
}
