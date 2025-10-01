package com.github.username.service;

import com.github.username.entity.Payment;
import com.github.username.entity.User;
import com.github.username.repository.PaymentRepository;
import com.github.username.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Сервис для обработки платежей через Stripe
 * Создает платежные сессии и обрабатывает вебхуки
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    @Value("${stripe.secret.key:}")
    private String stripeSecretKey;

    @Value("${stripe.success.url:}")
    private String successUrl;

    @Value("${stripe.cancel.url:}")
    private String cancelUrl;

    /**
     * Инициализация Stripe API ключа
     */
    @PostConstruct
    public void init() {
        if (stripeSecretKey != null && !stripeSecretKey.isEmpty()) {
            Stripe.apiKey = stripeSecretKey;
            log.info("Stripe initialized successfully");
        } else {
            log.warn("Stripe secret key is not set");
        }
    }

    /**
     * Создает сессию оплаты в Stripe
     *
     * @param chatId ID чата пользователя
     * @param packageType тип пакета (10, 50, 100 запросов)
     * @return URL для redirect на страницу оплаты Stripe
     */
    public String createPaymentSession(Long chatId, String packageType) {
        if (stripeSecretKey == null || stripeSecretKey.isEmpty()) {
            log.error("Stripe is not configured");
            return null;
        }

        // Определяем стоимость и количество запросов по типу пакета
        Map<String, Object> packageInfo = getPackageInfo(packageType);
        if (packageInfo == null) {
            log.error("Invalid package type: {}", packageType);
            return null;
        }

        long amount = (long) packageInfo.get("amount");
        int requests = (int) packageInfo.get("requests");
        String description = (String) packageInfo.get("description");

        try {
            // Создаем параметры для сессии Stripe Checkout
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("usd")
                                                    .setUnitAmount(amount) // Сумма в центах
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName(description)
                                                                    .setDescription("Пакет запросов к AI помощнику")
                                                                    .build())
                                                    .build())
                                    .build())
                    .putMetadata("chatId", chatId.toString())
                    .putMetadata("packageType", packageType)
                    .putMetadata("requests", String.valueOf(requests))
                    .build();

            // Создаем сессию в Stripe
            Session session = Session.create(params);

            // Сохраняем информацию о платеже в базу
            savePendingPayment(chatId, session.getId(), amount, requests);

            log.info("Created Stripe session {} for user {}", session.getId(), chatId);
            return session.getUrl();

        } catch (StripeException e) {
            log.error("Error creating Stripe session", e);
            return null;
        }
    }

    /**
     * Возвращает информацию о доступных пакетах
     */
    public String getPaymentOptions(Long chatId) {
        return "💳 Выберите пакет запросов:\n\n" +
                "1. 10 запросов - $1.00\n" +
                "   Команда: /buy_10\n\n" +
                "2. 50 запросов - $4.00 (экономия $1.00)\n" +
                "   Команда: /buy_50\n\n" +
                "3. 100 запросов - $7.00 (экономия $3.00)\n" +
                "   Команда: /buy_100\n\n" +
                "После оплаты запросы автоматически добавятся к вашему балансу!";
    }

    /**
     * Обрабатывает успешный платеж из Stripe webhook
     */
    public void handleSuccessfulPayment(String stripeSessionId) {
        try {
            // Получаем информацию о сессии из Stripe
            Session session = Session.retrieve(stripeSessionId);

            // Извлекаем метаданные
            String chatIdStr = session.getMetadata().get("chatId");
            String packageType = session.getMetadata().get("packageType");
            String requestsStr = session.getMetadata().get("requests");

            if (chatIdStr == null || requestsStr == null) {
                log.error("Missing metadata in Stripe session: {}", stripeSessionId);
                return;
            }

            Long chatId = Long.parseLong(chatIdStr);
            int requests = Integer.parseInt(requestsStr);

            // Находим пользователя
            Optional<User> userOpt = userRepository.findById(chatId);
            if (userOpt.isEmpty()) {
                log.error("User not found for chatId: {}", chatId);
                return;
            }

            // Обновляем баланс пользователя
            User user = userOpt.get();
            double amountPerRequest = getAmountPerRequest(packageType);
            double totalAmount = requests * amountPerRequest;

            user.setBalance(user.getBalance() + totalAmount);
            userRepository.save(user);

            // Обновляем статус платежа в базе
            updatePaymentStatus(stripeSessionId, "completed");

            // Отправляем уведомление пользователю
            log.info("Payment completed for user {}: {} requests (${})",
                    chatId, requests, totalAmount);

        } catch (StripeException e) {
            log.error("Error retrieving Stripe session", e);
        } catch (NumberFormatException e) {
            log.error("Invalid metadata format in Stripe session", e);
        }
    }

    /**
     * Вспомогательный метод - информация о пакетах
     */
    private Map<String, Object> getPackageInfo(String packageType) {
        Map<String, Object> info = new HashMap<>();

        switch (packageType) {
            case "10":
                info.put("amount", 100L); // $1.00 в центах
                info.put("requests", 10);
                info.put("description", "10 запросов к AI");
                break;
            case "50":
                info.put("amount", 400L); // $4.00 в центах
                info.put("requests", 50);
                info.put("description", "50 запросов к AI");
                break;
            case "100":
                info.put("amount", 700L); // $7.00 в центах
                info.put("requests", 100);
                info.put("description", "100 запросов к AI");
                break;
            default:
                return null;
        }

        return info;
    }

    /**
     * Вспомогательный метод - стоимость одного запроса в пакете
     */
    private double getAmountPerRequest(String packageType) {
        switch (packageType) {
            case "10": return 0.10; // $0.10 за запрос
            case "50": return 0.08; // $0.08 за запрос
            case "100": return 0.07; // $0.07 за запрос
            default: return 0.10;
        }
    }

    /**
     * Сохраняет информацию о pending платеже
     */
    private void savePendingPayment(Long chatId, String stripePaymentId, long amount, int requests) {
        Optional<User> userOpt = userRepository.findById(chatId);
        if (userOpt.isPresent()) {
            Payment payment = new Payment();
            payment.setUser(userOpt.get());
            payment.setStripePaymentId(stripePaymentId);
            payment.setAmount(amount / 100.0); // Конвертируем центы в доллары
            payment.setCurrency("USD");
            payment.setStatus("pending");
            payment.setRequestsPurchased(requests);

            paymentRepository.save(payment);
        }
    }

    /**
     * Обновляет статус платежа
     */
    private void updatePaymentStatus(String stripePaymentId, String status) {
        paymentRepository.findAll().stream()
                .filter(p -> stripePaymentId.equals(p.getStripePaymentId()))
                .findFirst()
                .ifPresent(payment -> {
                    payment.setStatus(status);
                    paymentRepository.save(payment);
                });
    }
}
