package com.github.username.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.LabeledPrice;
import com.pengrad.telegrambot.request.SendInvoice;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPaymentService {

    private final TelegramBot bot;
    private final UserService userService;
    private final TelegramMessageService messageService;

    @Value("${telegram.payments.provider.token:}")
    private String providerToken;

    @Value("${payment.currency:RUB}")
    private String currency;

    private final Map<String, String> paymentSessions = new HashMap<>();

    /**
     * Отправляет счет пользователю через Telegram Payments
     */
    public boolean sendInvoice(Long chatId, String packageType) {
        if (providerToken == null || providerToken.isEmpty()) {
            log.error("Payment provider token is not configured");
            return false;
        }

        PackageInfo packageInfo = getPackageInfo(packageType);
        if (packageInfo == null) {
            log.error("Invalid package type: {}", packageType);
            return false;
        }

        try {
            // Создаем уникальный payload для идентификации платежа
            String payload = UUID.randomUUID().toString();

            // Сохраняем связь payload -> packageType
            paymentSessions.put(payload, packageType);

            log.info("Creating invoice for user {}: package {}, payload {}",
                    chatId, packageType, payload);

            // ✅ ВАРИАНТ 1: Попробуем без currency (возможно он берется из провайдера)
            LabeledPrice[] prices = new LabeledPrice[]{
                    new LabeledPrice(packageInfo.getDescription(), packageInfo.getPrice())
            };

            SendInvoice invoice = new SendInvoice(
                    chatId,
                    packageInfo.getTitle(),
                    packageInfo.getDescription(),
                    payload,
                    providerToken,
                    prices  // Только prices без currency
            )
                    .needPhoneNumber(false)
                    .needEmail(false)
                    .needShippingAddress(false)
                    .isFlexible(false)
                    .sendPhoneNumberToProvider(false)
                    .sendEmailToProvider(false);

            SendResponse response = bot.execute(invoice);

            if (response.isOk()) {
                log.info("✅ Invoice sent successfully to user {} for package {}", chatId, packageType);
                return true;
            } else {
                log.error("❌ Failed to send invoice: {}", response.description());
                return false;
            }

        } catch (Exception e) {
            log.error("❌ Error sending invoice", e);
            return false;
        }
    }

    /**
     * Альтернативный метод если первый не работает
     */
    public boolean sendInvoiceAlternative(Long chatId, String packageType) {
        try {
            PackageInfo packageInfo = getPackageInfo(packageType);
            if (packageInfo == null) return false;

            String payload = UUID.randomUUID().toString();
            paymentSessions.put(payload, packageType);

            log.info("Creating invoice (alternative) for user {}: package {}", chatId, packageType);

            // ✅ ВАРИАНТ 2: Используем builder pattern если доступен
            LabeledPrice price = new LabeledPrice(packageInfo.getDescription(), packageInfo.getPrice());

            SendInvoice invoice = new SendInvoice(
                    chatId,
                    packageInfo.getTitle(),
                    packageInfo.getDescription(),
                    payload,
                    providerToken,
                    price  // Передаем один price объект
            );

            // Добавляем дополнительные параметры
            invoice.needPhoneNumber(false)
                    .needEmail(false)
                    .needShippingAddress(false)
                    .isFlexible(false);

            SendResponse response = bot.execute(invoice);

            return response.isOk();

        } catch (Exception e) {
            log.error("❌ Error sending invoice (alternative)", e);
            return false;
        }
    }

    /**
     * Обрабатывает успешный платеж
     */
    public void handleSuccessfulPayment(String payload, Long chatId) {
        try {
            String packageType = paymentSessions.get(payload);
            if (packageType == null) {
                log.error("❌ Payment session not found for payload: {}", payload);
                return;
            }

            paymentSessions.remove(payload);

            PackageInfo packageInfo = getPackageInfo(packageType);
            if (packageInfo != null) {
                double amountInUsd = convertToUsd(packageInfo.getPrice(), packageInfo.getCurrency());
                userService.updateUserBalance(chatId, amountInUsd);

                double newBalance = getUserBalance(chatId);

                log.info("✅ Payment processed for user {}: {} requests (${})",
                        chatId, packageInfo.getRequests(), amountInUsd);

                String message = "✅ **Оплата прошла успешно!** 🎉\n\n" +
                        "💳 Сумма: " + (packageInfo.getPrice() / 100) + " RUB\n" +
                        "📦 Получено запросов: " + packageInfo.getRequests() + "\n" +
                        "💰 Новый баланс: $" + String.format("%.2f", newBalance) + "\n\n" +
                        "Теперь вы можете продолжать общение с ботом! 🚀";

                messageService.sendMessage(chatId, message);
            }
        } catch (Exception e) {
            log.error("❌ Error processing payment", e);
        }
    }

    /**
     * Метод для получения баланса пользователя
     */
    private double getUserBalance(Long chatId) {
        try {
            String stats = userService.getUserStats(chatId);
            if (stats.contains("Баланс: $")) {
                int start = stats.indexOf("Баланс: $") + 9;
                int end = stats.indexOf("\n", start);
                if (end == -1) end = stats.length();

                String balanceStr = stats.substring(start, end).trim();
                return Double.parseDouble(balanceStr);
            }
        } catch (Exception e) {
            log.error("❌ Error parsing user balance", e);
        }
        return 0.0;
    }

    public String getPackageTypeByPayload(String payload) {
        return paymentSessions.get(payload);
    }

    private PackageInfo getPackageInfo(String packageType) {
        switch (packageType) {
            case "10":
                return new PackageInfo("10 запросов к AI",
                        "Пакет из 10 запросов к ChatGPT",
                        10000, "RUB", 10);
            case "50":
                return new PackageInfo("50 запросов к AI",
                        "Пакет из 50 запросов к ChatGPT (выгодно!)",
                        40000, "RUB", 50);
            case "100":
                return new PackageInfo("100 запросов к AI",
                        "Пакет из 100 запросов к ChatGPT (максимальная выгода!)",
                        70000, "RUB", 100);
            default:
                return null;
        }
    }

    private double convertToUsd(int amount, String currency) {
        if ("RUB".equals(currency)) {
            return amount / 100.0 / 90.0;
        }
        return amount / 100.0;
    }

    private static class PackageInfo {
        private final String title;
        private final String description;
        private final int price;
        private final String currency;
        private final int requests;

        public PackageInfo(String title, String description, int price, String currency, int requests) {
            this.title = title;
            this.description = description;
            this.price = price;
            this.currency = currency;
            this.requests = requests;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public int getPrice() { return price; }
        public String getCurrency() { return currency; }
        public int getRequests() { return requests; }
    }
}