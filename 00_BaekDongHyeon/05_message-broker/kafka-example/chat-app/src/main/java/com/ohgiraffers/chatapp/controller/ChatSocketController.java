package com.ohgiraffers.chatapp.controller;

import com.ohgiraffers.chatapp.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/* STOMP 메시지를 받아 Kafka로 전달하는 컨트롤러
 * - @MessageMapping: 클라이언트가 '/app/chat.send' 로 메시지 발행 시 호출
 */
@Controller
@RequiredArgsConstructor
public class ChatSocketController {

    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;

    /* 클라이언트로부터 들어온 채팅 메시지를 Kafka 토픽으로 전송
     * - "chat-topic-{roomId}" 이름의 토픽으로, roomId 별로 메시지를 분리 가능
     */
    @MessageMapping("/chat.send")
    public void send(ChatMessage message) {
        // KafkaTemplate.send(topicName, ChatMessage 객체)
        kafkaTemplate.send("chat-topic-" + message.roomId(), message);
    }
}
