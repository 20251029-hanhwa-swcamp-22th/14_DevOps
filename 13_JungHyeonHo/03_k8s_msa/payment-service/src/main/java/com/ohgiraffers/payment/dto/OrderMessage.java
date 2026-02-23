package com.ohgiraffers.payment.dto;

public record OrderMessage(Integer orderId, String productId, int quantity, String userId) {}

