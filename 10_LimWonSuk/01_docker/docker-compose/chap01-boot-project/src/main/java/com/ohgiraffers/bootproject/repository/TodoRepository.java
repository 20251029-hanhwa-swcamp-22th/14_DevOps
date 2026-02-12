package com.ohgiraffers.bootproject.repository;

import com.ohgiraffers.bootproject.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository // 안해도되지만 명시적으로 사용
public interface TodoRepository extends JpaRepository<Todo, Long> {
}
