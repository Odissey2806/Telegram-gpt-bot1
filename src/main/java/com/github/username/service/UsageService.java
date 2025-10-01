package com.github.username.service;

import com.github.username.entity.UsageHistory;
import com.github.username.entity.User;
import com.github.username.repository.UsageRepository;
import com.github.username.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Сервис для учета использования бота
 * Отвечает за подсчет запросов, лимиты и историю использования
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageService {

    private final UsageRepository usageRepository;
    private final UserRepository userRepository;

    @Value("${usage.daily.free:10}")
    private int dailyFreeLimit;

    @Value("${usage.price.per.request:0.10}")
    private double pricePerRequest;

    /**
     * Регистрирует использование бота пользователем
     * Проверяет лимиты, списывает баланс при необходимости
     */
    @Transactional
    public boolean registerUsage(Long chatId, String message, String response) {
        Optional<User> userOpt = userRepository.findById(chatId);
        if (userOpt.isEmpty()) {
            log.error("User not found for chatId: {}", chatId);
            return false;
        }

        User user = userOpt.get();
        user.resetDailyLimitIfNeeded(); // Сбрасываем счетчик если новый день

        // Проверяем можно ли сделать запрос
        if (!canMakeRequest(user)) {
            log.warn("User {} exceeded limits", chatId);
            return false;
        }

        // Определяем стоимость запроса
        double cost = 0.0;
        if (user.getDailyRequestsUsed() >= dailyFreeLimit) {
            cost = pricePerRequest; // Списание баланса после бесплатных запросов
        }

        // Проверяем достаточно ли баланса для платных запросов
        if (cost > 0 && user.getBalance() < cost) {
            log.warn("Insufficient balance for user {}: ${} < ${}",
                    chatId, user.getBalance(), cost);
            return false;
        }

        // Обновляем счетчики пользователя
        user.setDailyRequestsUsed(user.getDailyRequestsUsed() + 1);
        user.setTotalRequests(user.getTotalRequests() + 1);
        user.setLastRequestDate(LocalDateTime.now());

        if (cost > 0) {
            user.setBalance(user.getBalance() - cost);
            log.info("Charged user {}: ${} for request", chatId, cost);
        }

        userRepository.save(user);

        // Сохраняем историю использования
        UsageHistory usage = new UsageHistory();
        usage.setUser(user);
        usage.setMessage(truncateMessage(message, 4000));
        usage.setResponse(truncateMessage(response, 4000));
        usage.setTokensUsed(calculateTokens(response));
        usage.setCost(cost);

        usageRepository.save(usage);

        log.debug("Registered usage for user {}: daily={}/{}",
                chatId, user.getDailyRequestsUsed(), dailyFreeLimit);

        return true;
    }

    /**
     * Проверяет может ли пользователь сделать запрос
     */
    public boolean canMakeRequest(User user) {
        user.resetDailyLimitIfNeeded();

        // Если не превышен дневной лимит - можно делать бесплатно
        if (user.getDailyRequestsUsed() < dailyFreeLimit) {
            return true;
        }

        // Если превышен лимит, проверяем баланс
        return user.getBalance() >= pricePerRequest;
    }

    /**
     * Возвращает информацию о лимитах пользователя
     */
    public String getUsageInfo(Long chatId) {
        Optional<User> userOpt = userRepository.findById(chatId);
        if (userOpt.isEmpty()) {
            return "Пользователь не найден";
        }

        User user = userOpt.get();
        user.resetDailyLimitIfNeeded();

        int remainingFree = Math.max(0, dailyFreeLimit - user.getDailyRequestsUsed());
        int remainingPaid = (int) (user.getBalance() / pricePerRequest);

        StringBuilder info = new StringBuilder();
        info.append("📊 Лимиты использования:\n\n");
        info.append("• Бесплатных запросов сегодня: ")
                .append(user.getDailyRequestsUsed()).append("/").append(dailyFreeLimit)
                .append(" (осталось: ").append(remainingFree).append(")\n");

        if (remainingFree == 0) {
            info.append("• Доступно платных запросов: ").append(remainingPaid).append("\n");
            info.append("• Стоимость запроса: $").append(String.format("%.2f", pricePerRequest)).append("\n");
            info.append("• Ваш баланс: $").append(String.format("%.2f", user.getBalance())).append("\n");
        }

        info.append("\n💳 Пополнить баланс: /payment");

        return info.toString();
    }

    /**
     * Вспомогательный метод для расчета примерного количества токенов
     */
    private int calculateTokens(String text) {
        // Простой расчет: примерно 1 токен = 4 символа
        // В реальном проекте лучше использовать токенизатор OpenAI
        return text.length() / 4;
    }

    /**
     * Вспомогательный метод для обрезки длинных сообщений
     */
    private String truncateMessage(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
