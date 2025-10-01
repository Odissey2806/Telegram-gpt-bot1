package com.github.username.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.username.service.TelegramBotService;
import com.github.username.service.TelegramMessageService;
import com.github.username.service.PaymentService; // ← ДОБАВЬ ЭТОТ ИМПОРТ
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class TelegramWebhookController {
    private final TelegramBotService botService;
    private final TelegramMessageService messageService;
    private final PaymentService paymentService; // ← ДОБАВЬ ЭТУ ЗАВИСИМОСТЬ

    @Value("${bot.webhook.secret:}")
    private String expectedSecret;

    @PostMapping
    public ResponseEntity<String> handleUpdate(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody JsonNode update) {

        // Проверка секрета
        if (expectedSecret != null && !expectedSecret.isEmpty() &&
                !expectedSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Invalid secret");
        }

        try {
            // Обработка сообщения
            processUpdate(update);
            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Error processing update", e);
            return ResponseEntity.ok("OK"); // Всегда возвращаем 200 Telegram
        }
    }

    private void processUpdate(JsonNode update) {
        // Обработка текстовых сообщений
        if (update.has("message") && update.get("message").has("text")) {
            JsonNode message = update.get("message");
            Long chatId = message.get("chat").get("id").asLong();
            String text = message.get("text").asText();
            String firstName = message.get("chat").get("first_name").asText();
            String lastName = message.get("chat").has("last_name") ?
                    message.get("chat").get("last_name").asText() : "";
            String username = message.get("chat").has("username") ?
                    message.get("chat").get("username").asText() : "";

            // Обработка команд
            if (text.startsWith("/")) {
                handleCommand(chatId, text, firstName);
            } else {
                // Обработка обычных сообщений
                handleTextMessage(chatId, text, firstName, lastName, username);
            }
        }

        // Обработка callback_query для кнопок оплаты
        if (update.has("callback_query")) {
            handleCallbackQuery(update.get("callback_query"));
        }
    }

    private void handleCommand(Long chatId, String command, String firstName) {
        switch (command) {
            case "/start":
                String welcomeMessage = "🤖 Добро пожаловать, " + firstName + "!\n\n" +
                        "Я - AI помощник на основе ChatGPT. Задавайте мне любые вопросы!\n\n" +
                        "📊 Статистика: /stats\n" +
                        "💳 Пополнить баланс: /payment\n" +
                        "🆘 Помощь: /help\n\n" +
                        "Сегодня у вас " + 10 + " бесплатных запросов!";
                messageService.sendMessage(chatId, welcomeMessage);
                break;

            case "/stats":
                String stats = botService.getUserStats(chatId);
                messageService.sendMessage(chatId, stats);
                break;

            case "/payment":
                // ✅ ТЕПЕРЬ paymentService ДОСТУПЕН
                String paymentInfo = paymentService.getPaymentOptions(chatId);
                messageService.sendMessage(chatId, paymentInfo);
                break;

            case "/buy_10":
                handleBuyCommand(chatId, "10");
                break;

            case "/buy_50":
                handleBuyCommand(chatId, "50");
                break;

            case "/buy_100":
                handleBuyCommand(chatId, "100");
                break;

            case "/help":
                String helpMessage = "🆘 Помощь по боту:\n\n" +
                        "• Просто напишите вопрос - и я отвечу!\n" +
                        "• Бесплатно: 10 запросов в день\n" +
                        "• После лимита - пополняйте баланс\n" +
                        "• 10 запросов = 1$\n" +
                        "• 50 запросов = 4$ (экономия 1$)\n" +
                        "• 100 запросов = 7$ (экономия 3$)\n\n" +
                        "Команды:\n" +
                        "/start - начать работу\n" +
                        "/stats - ваша статистика\n" +
                        "/payment - пополнить баланс\n" +
                        "/help - эта справка";
                messageService.sendMessage(chatId, helpMessage);
                break;

            default:
                messageService.sendMessage(chatId, "Неизвестная команда. Используйте /help для справки.");
        }
    }

    /**
     * Обрабатывает команды покупки пакетов запросов
     */
    private void handleBuyCommand(Long chatId, String packageType) {
        try {
            String paymentUrl = paymentService.createPaymentSession(chatId, packageType);
            if (paymentUrl != null) {
                String message = "💳 Для оплаты перейдите по ссылке:\n" + paymentUrl +
                        "\n\nПосле успешной оплаты запросы автоматически добавятся к вашему балансу!";
                messageService.sendMessage(chatId, message);
            } else {
                messageService.sendMessage(chatId, "❌ Не удалось создать платежную сессию. Попробуйте позже.");
            }
        } catch (Exception e) {
            log.error("Error creating payment session for user {}", chatId, e);
            messageService.sendMessage(chatId, "❌ Произошла ошибка при создании платежа.");
        }
    }

    /**
     * Обрабатывает callback_query от inline кнопок
     */
    private void handleCallbackQuery(JsonNode callbackQuery) {
        // Здесь можно обрабатывать нажатия на inline кнопки
        // Например, для подтверждения платежа или выбора тарифа
        log.info("Callback query received: {}", callbackQuery);
    }

    private void handleTextMessage(Long chatId, String text, String firstName, String lastName, String username) {
        // Обрабатываем сообщение асинхронно
        new Thread(() -> {
            try {
                // ✅ ИСПРАВЛЕНО: processMessage теперь void, не ожидаем возвращаемое значение
                botService.processMessage(chatId, text, firstName, lastName, username);
                // Ответ будет отправлен внутри processMessage через messageService
            } catch (Exception e) {
                log.error("Error processing message", e);
                messageService.sendMessage(chatId, "⚠️ Произошла ошибка. Пожалуйста, попробуйте позже.");
            }
        }).start();
    }
}