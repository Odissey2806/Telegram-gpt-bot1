package com.github.username.config;

import com.pengrad.telegrambot.TelegramBot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурация бинов для Telegram бота и других сервисов
 */
@Configuration
public class BotConfig {

    @Value("${bot.token}")
    private String botToken;

    /**
     * Создает бин TelegramBot для работы с Telegram API
     */
    @Bean
    public TelegramBot telegramBot() {
        return new TelegramBot(botToken);
    }

    /**
     * Создает бин RestTemplate для HTTP запросов к OpenAI
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}