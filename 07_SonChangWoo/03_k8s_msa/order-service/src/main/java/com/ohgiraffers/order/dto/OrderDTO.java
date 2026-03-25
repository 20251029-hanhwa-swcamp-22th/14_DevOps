package com.ohgiraffers.order.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderDTO {
    private String userId;
    private String productId;
    private int quantity;
}
