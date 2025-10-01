package com.github.username.dto;

import lombok.Data;

/**
 * DTO для запроса на создание платежа
 * Используется для приема данных от клиента
 */
@Data
public class PaymentRequest {

    private Long chatId; // ID пользователя в Telegram
    private String packageType; // Тип пакета: "10", "50", "100"
    private String currency; // Валюта: "USD", "EUR" и т.д.

    /**
     * Проверяет валидность запроса
     */
    public boolean isValid() {
        return chatId != null &&
                packageType != null &&
                (packageType.equals("10") || packageType.equals("50") || packageType.equals("100")) &&
                currency != null && currency.length() == 3;
    }
}
