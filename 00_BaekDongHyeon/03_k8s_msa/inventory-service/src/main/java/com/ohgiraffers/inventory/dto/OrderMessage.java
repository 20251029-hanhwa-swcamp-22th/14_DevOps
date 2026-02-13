package com.ohgiraffers.inventory.dto;

public record OrderMessage(Integer orderId, String productId, int quantity, String userId) {}

