package com.ohgiraffers.bootproject.controller;

import com.ohgiraffers.bootproject.dto.TodoRequestDto;
import com.ohgiraffers.bootproject.dto.TodoResponseDto;
import com.ohgiraffers.bootproject.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/todos")
public class TodoController {

  private final TodoService todoService;

  @GetMapping
  public ResponseEntity<List<TodoResponseDto>> getAllTodos() {
    log.info("Get all todos");
    List<TodoResponseDto> todos = todoService.findAll();
    return ResponseEntity.ok(todos);
  }

  @PostMapping
  public ResponseEntity<TodoResponseDto> createTodo(@RequestBody TodoRequestDto requestDto) {
    log.info("Create todo: {}", requestDto.getTitle());
    TodoResponseDto todo = todoService.create(requestDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(todo);
  }


  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteTodo(@PathVariable Long id) {
    log.info("Delete todo: {}", id);
    todoService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/toggle")
  public ResponseEntity<TodoResponseDto> toggleTodo(@PathVariable Long id) {
    log.info("Toggle todo: {}", id);
    TodoResponseDto todo = todoService.toggle(id);
    return ResponseEntity.ok(todo);
  }
}