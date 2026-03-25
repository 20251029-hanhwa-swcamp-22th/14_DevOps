package com.ohgiraffers.bootproject.dto;

import com.ohgiraffers.bootproject.entity.Todo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TodoResponseDto {
  private Long id;
  private String title;
  private Boolean completed;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static TodoResponseDto from(Todo todo) {
    return new TodoResponseDto(
        todo.getId(),
        todo.getTitle(),
        todo.getCompleted(),
        todo.getCreatedAt(),
        todo.getUpdatedAt()
    );
  }
}