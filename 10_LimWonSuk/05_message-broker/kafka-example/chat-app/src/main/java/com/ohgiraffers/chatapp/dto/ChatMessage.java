package com.ohgiraffers.chatapp.dto;

// Kafka로 주고받는 메시지 형식
public record ChatMessage(String roomId, String sender, String message, String timestamp) {}
