package com.example.websocketstomp.chat;

import java.time.LocalDateTime;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    @MessageMapping("/chat.send")
    @SendTo("/topic/public")
    public ChatMessage send(@Payload ChatMessage message) {
        message.setSentAt(LocalDateTime.now());
        return message;
    }
}
