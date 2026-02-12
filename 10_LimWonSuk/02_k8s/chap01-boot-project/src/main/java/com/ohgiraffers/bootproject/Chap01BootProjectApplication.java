package com.ohgiraffers.bootproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication // spring 설정 클래스 등록 어노테이션
public class Chap01BootProjectApplication {

    public static void main(String[] args) {
        // SpringApplication.run 호출시 IoC컨테이너 생성 -> Bean 자동등록 -> 의존성 주입 자동화
        SpringApplication.run(Chap01BootProjectApplication.class, args);
    }

}
