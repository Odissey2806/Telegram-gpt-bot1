package com.github.username.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Сущность пользователя Telegram
 * Хранит информацию о пользователе, его лимитах и балансе
 */
@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    private Long chatId; // ID чата в Telegram используется как первичный ключ

    @Column(nullable = false)
    private String firstName; // Имя пользователя

    private String lastName; // Фамилия пользователя (может быть null)
    private String username; // @username в Telegram (может быть null)

    @Column(nullable = false)
    private String plan = "FREE"; // Тарифный план: FREE или PREMIUM

    private int dailyRequestsUsed = 0; // Количество использованных запросов сегодня
    private LocalDateTime lastRequestDate; // Дата последнего запроса

    private int totalRequests = 0; // Общее количество запросов
    private double balance = 0.0; // Баланс в долларах

    private LocalDateTime registeredAt; // Дата регистрации
    private LocalDateTime lastActivity; // Дата последней активности

    /**
     * Автоматически устанавливает даты при создании
     */
    @PrePersist
    protected void onCreate() {
        registeredAt = LocalDateTime.now();
        lastActivity = LocalDateTime.now();
    }

    /**
     * Автоматически обновляет дату при изменении
     */
    @PreUpdate
    protected void onUpdate() {
        lastActivity = LocalDateTime.now();
    }

    /**
     * Сбрасывает дневной лимит если наступил новый день
     */
    public void resetDailyLimitIfNeeded() {
        if (lastRequestDate == null ||
                lastRequestDate.toLocalDate().isBefore(LocalDateTime.now().toLocalDate())) {
            dailyRequestsUsed = 0;
            lastRequestDate = LocalDateTime.now();
        }
    }
}