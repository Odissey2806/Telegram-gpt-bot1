package com.github.username.repository;

import com.github.username.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для работы с пользователями
 * Наследует все стандартные методы CRUD от JpaRepository
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Spring Data JPA автоматически реализует методы:
    // save(), findById(), findAll(), deleteById() и т.д.
    // Здесь можно добавить кастомные запросы при необходимости
}
