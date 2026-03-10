package com.ohgiraffers.chatapp.config;

import com.ohgiraffers.chatapp.dto.ChatMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/* Kafka 설정 클래스
 * @EnableKafka 어노테이션으로 Spring이 Kafka 리스너를 감지하고 필요한 빈을 생성하도록 활성화
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    // application.yml 또는 application.properties에서 그룹 아이디를 주입
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /* ProducerFactory 설정
     * - Kafka에 메시지를 보내는(produce) 역할을 수행
     * - 서버 주소, 키/값 직렬화 방식 등을 지정
     */
    @Bean
    public ProducerFactory<String, ChatMessage> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        // 카프카 클러스터의 호스트:포트 (Docker Compose나 외부 서버 등)
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");

        // 메시지 키를 String 으로 직렬화
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 메시지 값을 JSON 으로 직렬화 (ChatMessage 객체 → JSON 문자열)
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new DefaultKafkaProducerFactory<>(config);
    }

    /* KafkaTemplate 빈 등록
     * - 메시지를 보내기 위한 편리한 API 제공
     * - producerFactory를 통해 생성된 프로듀서를 내부에서 사용
     */
    @Bean
    public KafkaTemplate<String, ChatMessage> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /* ConsumerFactory 설정
     * - Kafka에서 메시지를 읽어오는(consume) 역할을 수행
     * - 그룹 아이디, 자동 오프셋 리셋 전략, 역직렬화 방식 등을 지정
     */
    @Bean
    public ConsumerFactory<String, ChatMessage> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        // 카프카 클러스터의 호스트:포트
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        // 동일한 그룹 아이디를 가진 컨슈머끼리 메시지 분산 처리
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // 컨슈머가 처음 시작할 때 가장 오래된 메시지부터 읽도록 설정
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // JSON 문자열 → ChatMessage 객체로 변환해주는 디시리얼라이저
        JsonDeserializer<ChatMessage> deserializer = new JsonDeserializer<>(ChatMessage.class);
        // 신뢰할 수 있는 패키지(*)를 지정해야 객체 변환 시 예외가 발생하지 않음
        deserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),   // 키 String 역직렬화
                deserializer                // 값 JSON 역직렬화
        );
    }

    /* KafkaListenerContainerFactory 설정
     * - @KafkaListener 어노테이션이 붙은 메서드를 실행할 컨테이너 생성
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ChatMessage> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ChatMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
