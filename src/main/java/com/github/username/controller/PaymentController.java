package com.github.username.controller;

import com.github.username.service.PaymentService;
import com.github.username.service.TelegramMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер для обработки платежей и Stripe webhook
 */
@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final TelegramMessageService telegramMessageService;

    /**
     * Обрабатывает вебхуки от Stripe
     * Stripe отправляет сюда уведомления о статусе платежей
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        log.info("Received Stripe webhook");

        try {
            // В реальном проекте здесь должна быть проверка подписи Stripe
            // Для упрощения пропускаем эту проверку в демо-версии

            // Парсим JSON и обрабатываем событие
            if (payload.contains("checkout.session.completed")) {
                // Извлекаем session_id из payload
                String sessionId = extractSessionIdFromPayload(payload);
                if (sessionId != null) {
                    paymentService.handleSuccessfulPayment(sessionId);

                    // Можно отправить уведомление пользователю
                    // Для этого нужно хранить chatId в сессии платежа
                }
            }

            return ResponseEntity.ok("Webhook processed");

        } catch (Exception e) {
            log.error("Error processing Stripe webhook", e);
            return ResponseEntity.status(500).body("Error processing webhook");
        }
    }

    /**
     * Вспомогательный метод для извлечения session_id из JSON payload
     */
    private String extractSessionIdFromPayload(String payload) {
        try {
            // Упрощенный парсинг - в реальном проекте используйте JSON парсер
            if (payload.contains("\"id\":\"cs_")) {
                int start = payload.indexOf("\"id\":\"cs_") + 6;
                int end = payload.indexOf("\"", start);
                return payload.substring(start, end);
            }
        } catch (Exception e) {
            log.error("Error extracting session ID from payload", e);
        }
        return null;
    }
}