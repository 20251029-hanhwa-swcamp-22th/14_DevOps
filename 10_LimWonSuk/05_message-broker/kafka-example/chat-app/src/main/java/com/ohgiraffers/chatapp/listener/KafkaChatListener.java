package com.ohgiraffers.chatapp.listener;

import com.ohgiraffers.chatapp.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/* Kafka에서 메시지를 수신(listen)하고,
 * WebSocket/STOMP로 다시 브로드캐스트(broadcast) 해 주는 리스너 컴포넌트
 */
@Component
@RequiredArgsConstructor
public class KafkaChatListener {

    // WebSocket 세션에 메시지를 전송할 때 사용하는 템플릿
    // 내부적으로 STOMP 프로토콜을 통해 /topic 구독자에게 메시지를 전달
    private final SimpMessagingTemplate messagingTemplate;

    /* @KafkaListener 어노테이션으로 이 메서드를 Kafka 메시지 리스너로 등록
     * - topics: 구독할 Kafka 토픽 이름
     * - groupId: application.yml에 설정된 컨슈머 그룹 아이디를 참조
     * Kafka 토픽 "chat-topic-room1" 에 새 메시지가 들어오면 이 메서드가 호출
     */
    @KafkaListener(
            topics = "chat-topic-room1",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listen(ChatMessage message) {

        // WebSocket/STOMP 구독자에게 메시지 전달
        // "/topic/room/{roomId}" 경로를 구독 중인 클라이언트들에게 ChatMessage 객체를 전송
        messagingTemplate.convertAndSend(
                "/topic/room/" + message.roomId(),
                message
        );
    }
}
