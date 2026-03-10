package com.ohgiraffers.chatapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/* WebSocket + STOMP 설정 클래스
 * @EnableWebSocketMessageBroker 어노테이션으로 STOMP 기반 메시지 브로커 사용 활성화
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /* 메시지 브로커(내장 SimpleBroker) 설정
     * - enableSimpleBroker: 클라이언트가 구독할 수 있는 경로(prefix) 설정
     *   예) 클라이언트가 '/topic/room1' 을 구독하면 이 앱의 SimpleBroker가 메시지 전달
     * - setApplicationDestinationPrefixes: 클라이언트가 서버로 메시지를 보낼 때 사용할 prefix
     *   예) 클라이언트가 '/app/chat.send' 로 send 하면 @MessageMapping("/chat.send") 메서드 호출
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");      // 구독용 내부 브로커 활성화
        registry.setApplicationDestinationPrefixes("/app");  // 발행용 prefix
    }

    /* STOMP 엔드포인트 등록
     * - 클라이언트가 WebSocket 연결을 열 때 사용하는 URL
     * - SockJS 옵션을 켜면 WebSocket을 지원하지 않는 환경에서 fallback 가능
     * - setAllowedOriginPatterns("*") 로 모든 출처 허용 (개발 편의)
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")            // 연결할 엔드포인트 URL
                .setAllowedOriginPatterns("*")     // CORS 허용
                .withSockJS();                     // SockJS fallback 지원
    }
}
