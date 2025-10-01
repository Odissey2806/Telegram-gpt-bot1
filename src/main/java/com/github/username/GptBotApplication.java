package com.github.username;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Главный класс Spring Boot приложения
 *
 * @EnableAsync - включает асинхронное выполнение методов
 * Это важно для быстрой обработки webhook от Telegram
 */
@SpringBootApplication
@EnableAsync
public class GptBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(GptBotApplication.class, args);
    }
}