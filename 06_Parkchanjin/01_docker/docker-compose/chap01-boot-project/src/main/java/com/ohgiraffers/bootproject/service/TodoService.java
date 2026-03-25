package com.ohgiraffers.bootproject.service;

import com.ohgiraffers.bootproject.dto.TodoRequestDto;
import com.ohgiraffers.bootproject.dto.TodoResponseDto;
import com.ohgiraffers.bootproject.entity.Todo;
import com.ohgiraffers.bootproject.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TodoService {

  private final TodoRepository todoRepository;

  @Transactional(readOnly = true)
  public List<TodoResponseDto> findAll() {
    return todoRepository.findAll().stream()
        .map(TodoResponseDto::from)
        .collect(Collectors.toList());
  }


  @Transactional
  public TodoResponseDto create(TodoRequestDto requestDto) {
    Todo todo = new Todo();
    todo.setTitle(requestDto.getTitle());
    todo.setCompleted(false);

    Todo savedTodo = todoRepository.save(todo);
    return TodoResponseDto.from(savedTodo);
  }

  @Transactional
  public void delete(Long id) {
    if (!todoRepository.existsById(id)) {
      throw new RuntimeException("Todo not found with id: " + id);
    }
    todoRepository.deleteById(id);
  }

  @Transactional
  public TodoResponseDto toggle(Long id) {
    Todo todo = todoRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Todo not found with id: " + id));

    todo.setCompleted(!todo.getCompleted());

    Todo updatedTodo = todoRepository.save(todo);
    return TodoResponseDto.from(updatedTodo);
  }


}
