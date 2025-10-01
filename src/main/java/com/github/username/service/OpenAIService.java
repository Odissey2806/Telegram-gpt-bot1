package com.github.username.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для взаимодействия с OpenAI API
 * Отправляет запросы к ChatGPT и получает ответы
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {

    private final RestTemplate restTemplate;

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    @Value("${openai.model:gpt-3.5-turbo}")
    private String model;

    /**
     * Отправляет запрос к OpenAI API и возвращает ответ
     *
     * @param message текст запроса от пользователя
     * @return ответ от AI
     */
    public String getChatResponse(String message) {
        // Проверяем, установлен ли API ключ
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            log.error("OpenAI API key is not configured");
            return "❌ Сервис временно недоступен. Пожалуйста, попробуйте позже.";
        }

        String url = "https://api.openai.com/v1/chat/completions";

        // Настраиваем заголовки HTTP запроса
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        // Формируем тело запроса согласно OpenAI API
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", new Object[]{
                Map.of("role", "user", "content", message)
        });
        requestBody.put("max_tokens", 1000); // Ограничение длины ответа
        requestBody.put("temperature", 0.7); // Контроль случайности ответа

        // Создаем HTTP entity с заголовками и телом
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.info("Sending request to OpenAI API for message: {}",
                    message.substring(0, Math.min(50, message.length())));

            // Отправляем POST запрос к OpenAI API
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class);

            Map<String, Object> responseBody = response.getBody();

            // Извлекаем текст ответа из JSON структуры
            if (responseBody != null && responseBody.containsKey("choices")) {
                java.util.List<Map<String, Object>> choices =
                        (java.util.List<Map<String, Object>>) responseBody.get("choices");

                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> messageObj = (Map<String, Object>) choice.get("message");
                    String content = (String) messageObj.get("content");

                    log.info("Successfully received response from OpenAI");
                    return content;
                }
            }

            log.warn("Unexpected response format from OpenAI: {}", responseBody);
            return "Не удалось получить ответ от AI. Попробуйте еще раз.";

        } catch (Exception e) {
            log.error("Error calling OpenAI API", e);
            return "⚠️ Произошла ошибка при обращении к AI сервису. Пожалуйста, попробуйте позже.";
        }
    }
}
