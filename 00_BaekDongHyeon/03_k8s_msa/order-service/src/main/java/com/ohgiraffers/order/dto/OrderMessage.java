package com.ohgiraffers.order.dto;

/* Java 14부터 정식 도입된 record는 데이터를 담기 위한 클래스
 *
 * - 모든 필드는 final이며 불변
 * - 생성자, getter, toString(), equals(), hashCode() 등을 자동 생성
 * - DTO 역할에 적합하며 매우 간결하게 정의 가능
 *
 * 주문 정보를 RabbitMQ로 보낼 때 사용하는 메시지 모델이다.
 */
public record OrderMessage(
        Integer orderId,   // 주문 ID
        String productId,  // 상품 ID
        int quantity,      // 수량
        String userId      // 사용자 ID
) {}
