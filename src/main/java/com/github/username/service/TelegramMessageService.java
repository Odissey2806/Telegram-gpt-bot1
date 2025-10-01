package com.github.username.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramMessageService {

    private final TelegramBot bot; // ✅ Получаем бин через конструктор

    public boolean sendMessage(Long chatId, String text) {
        if (bot == null) {
            log.error("Bot not initialized");
            return false;
        }

        // Ограничиваем длину сообщения для Telegram (4096 символов)
        if (text.length() > 4096) {
            text = text.substring(0, 4090) + "...";
        }

        try {
            SendMessage request = new SendMessage(chatId, text);
            SendResponse response = bot.execute(request);

            if (response.isOk()) {
                log.debug("Message sent to chat {}: {}", chatId,
                        text.substring(0, Math.min(50, text.length())) + "...");
                return true;
            } else {
                log.error("Failed to send message to chat {}: {}", chatId, response.description());
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending message to chat {}", chatId, e);
            return false;
        }
    }

    // Дополнительный метод для отправки сообщений с обработкой ошибок
    public void sendMessageWithRetry(Long chatId, String text, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            if (sendMessage(chatId, text)) {
                return; // Успешно отправлено
            }

            try {
                Thread.sleep(1000 * (i + 1)); // Увеличиваем задержку при каждой попытке
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.error("Failed to send message to chat {} after {} retries", chatId, maxRetries);
    }
}