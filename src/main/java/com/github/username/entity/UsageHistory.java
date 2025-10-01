package com.github.username.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * История использования бота
 * Записывает каждый запрос и ответ для аналитики и отладки
 */
@Data
@Entity
@Table(name = "usage_history")
public class UsageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Автоинкрементный ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // Ссылка на пользователя

    @Column(length = 4000)
    private String message; // Запрос пользователя

    @Column(length = 4000)
    private String response; // Ответ от AI

    private int tokensUsed; // Примерное количество использованных токенов
    private double cost; // Стоимость запроса (для платных запросов)

    private LocalDateTime createdAt; // Время создания записи

    /**
     * Устанавливает время создания перед сохранением
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}