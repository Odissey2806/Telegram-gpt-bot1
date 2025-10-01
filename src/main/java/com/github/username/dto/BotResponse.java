package com.github.username.dto;

import lombok.Data;

/**
 * DTO для стандартизированных ответов бота
 * Может использоваться для API responses
 */
@Data
public class BotResponse {

    private boolean success; // Успех операции
    private String message; // Сообщение для пользователя
    private Object data; // Дополнительные данные

    /**
     * Создает успешный ответ
     */
    public static BotResponse success(String message) {
        BotResponse response = new BotResponse();
        response.setSuccess(true);
        response.setMessage(message);
        return response;
    }

    /**
     * Создает успешный ответ с данными
     */
    public static BotResponse success(String message, Object data) {
        BotResponse response = new BotResponse();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    /**
     * Создает ответ с ошибкой
     */
    public static BotResponse error(String message) {
        BotResponse response = new BotResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}
