package com.github.username.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO для парсинга обновлений от Telegram Webhook
 * Содержит только необходимые поля из Telegram API
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUpdate {

    @JsonProperty("update_id")
    private Long updateId;

    @JsonProperty("message")
    private TelegramMessage message;

    /**
     * Вложенный класс для сообщения
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramMessage {

        @JsonProperty("message_id")
        private Long messageId;

        @JsonProperty("from")
        private TelegramUser from;

        @JsonProperty("chat")
        private TelegramChat chat;

        @JsonProperty("text")
        private String text;

        @JsonProperty("date")
        private Long date;
    }

    /**
     * Вложенный класс для информации о пользователе
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramUser {

        @JsonProperty("id")
        private Long id;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        @JsonProperty("username")
        private String username;
    }

    /**
     * Вложенный класс для информации о чате
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramChat {

        @JsonProperty("id")
        private Long id;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        @JsonProperty("username")
        private String username;

        @JsonProperty("type")
        private String type; // "private", "group", "supergroup", "channel"
    }
}
