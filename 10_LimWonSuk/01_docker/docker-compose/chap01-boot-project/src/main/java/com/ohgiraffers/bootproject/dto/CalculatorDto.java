package com.ohgiraffers.bootproject.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class CalculatorDto {
    /* 모델어트리뷰트  생략된 커멘드객체*/
    private final Integer num1;
    private final Integer num2;
    private Integer sum;

    public CalculatorDto(Integer num1, Integer num2) {
        this.num1 = num1;
        this.num2 = num2;
    }

}
