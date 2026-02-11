package com.wjdqudwls.bootproject.repository;

import com.wjdqudwls.bootproject.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TodoRepository extends JpaRepository<Todo, Long> {

}
