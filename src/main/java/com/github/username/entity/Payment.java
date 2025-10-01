package com.github.username.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Сущность платежа через Stripe
 * Хранит информацию о финансовых операциях
 */
@Data
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Внутренний ID платежа

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // Пользователь, совершивший платеж

    private String stripePaymentId; // ID платежа в системе Stripe
    private double amount; // Сумма платежа
    private String currency; // Валюта (USD, EUR и т.д.)
    private String status; // Статус платежа: pending, completed, failed

    private int requestsPurchased; // Количество купленных запросов
    private LocalDateTime paymentDate; // Дата и время платежа

    /**
     * Устанавливает дату платежа перед сохранением
     */
    @PrePersist
    protected void onCreate() {
        paymentDate = LocalDateTime.now();
    }
}