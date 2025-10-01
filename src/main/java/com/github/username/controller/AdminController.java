package com.github.username.controller;

import com.github.username.entity.User;
import com.github.username.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер для административных функций
 * В реальном проекте должен быть защищен аутентификацией
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;

    /**
     * Получает список всех пользователей (для админки)
     */
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Получает статистику по пользователю
     */
    @GetMapping("/users/{chatId}")
    public User getUserStats(@PathVariable Long chatId) {
        return userRepository.findById(chatId).orElse(null);
    }
}