package com.ohgiraffers.bootproject.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ohgiraffers.bootproject.dto.CalculatorDto;
import com.ohgiraffers.bootproject.service.CalculatorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 로그 객체 자동 생성
@Slf4j
// Spring에게 "이 클래스는 HTTP 요청을 받아서 처리하는 클래스"
@RestController
// @Controller만 쓰면 리턴값을 HTML 파일 이름으로 인식합니다.
@RequiredArgsConstructor

// 공용 클래스(설계도) 이름CalculatorController 생성
public class CalculatorController {

    // 이클래스에서만 한번만 작동하는 calculatorService변수명을 가진 객체 생성
    // -> 이 클래스에서만 접근 가능하고,
    // 한 번 주입되면 변경할 수 없는 CalculatorService 의존성 주입
    private final CalculatorService calculatorService;

    // 조회방식으로 /health라고 요청하면
    @GetMapping("/health")

    // 공용 문자열인 healthCheck변수명을가진값(I'm alive)을 응답하겠다
    // ->  // 공용 클래스(설계도) 이름CalculatorController 생성
    public String healthCheck() {
        return "I'm alive";
    }

    // 조회방식으로 /plus라고 요청하면
    @GetMapping("/plus")

    // 공용 변수명이plusTwoNumbers이고 매개변수 (CalculatorDto calculatorDto)을 이용한 메서드를 응답하겠다
    // -> 쿼리 파라미터를 CalculatorDto로 받아서, 덧셈 결과를 담아 JSON으로 응답하는 메서드
    public ResponseEntity<CalculatorDto> plusTwoNumbers(CalculatorDto calculatorDto) {
        log.info("✨핸들러 메소드 실행 여부 및 값 확인!!!!!!!!! : {}", calculatorDto);

        /* Service 계층으로 기능 요청 */
        int result = calculatorService.plusTwoNumbers(calculatorDto);

        log.info("🔥서비스 계층 결과값 리턴 확인!!!!!!! {}", result);
        calculatorDto.setSum(result);

        return ResponseEntity.ok(calculatorDto);
    }
}
