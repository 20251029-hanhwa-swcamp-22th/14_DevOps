package com.ohgiraffers.payment.repository;

import com.ohgiraffers.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
