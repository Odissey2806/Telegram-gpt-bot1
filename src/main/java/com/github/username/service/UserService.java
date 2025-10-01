package com.github.username.service;

import com.github.username.entity.User;
import com.github.username.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Сервис для управления пользователями
 * Содержит бизнес-логику работы с пользователями
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Создает нового пользователя
     */
    @Transactional
    public User createUser(Long chatId, String firstName, String lastName, String username) {
        User user = new User();
        user.setChatId(chatId);
        user.setFirstName(firstName);
        user.setLastName(lastName != null ? lastName : "");
        user.setUsername(username != null ? username : "");
        user.setPlan("FREE");
        user.setDailyRequestsUsed(0);
        user.setTotalRequests(0);
        user.setBalance(0.0);

        User savedUser = userRepository.save(user);
        log.info("Created new user: {} (chatId: {})", firstName, chatId);

        return savedUser;
    }

    /**
     * Находит пользователя по chatId или создает нового
     */
    @Transactional
    public User findOrCreateUser(Long chatId, String firstName, String lastName, String username) {
        return userRepository.findById(chatId)
                .orElseGet(() -> createUser(chatId, firstName, lastName, username));
    }

    /**
     * Обновляет баланс пользователя
     */
    @Transactional
    public void updateUserBalance(Long chatId, double amount) {
        Optional<User> userOpt = userRepository.findById(chatId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setBalance(user.getBalance() + amount);
            userRepository.save(user);
            log.info("Updated balance for user {}: +${}", chatId, amount);
        }
    }

    /**
     * Получает статистику пользователя
     */
    public String getUserStats(Long chatId) {
        Optional<User> userOpt = userRepository.findById(chatId);
        if (userOpt.isEmpty()) {
            return "Пользователь не найден";
        }

        User user = userOpt.get();
        user.resetDailyLimitIfNeeded(); // Обновляем дневные лимиты

        return "📊 Ваша статистика:\n\n" +
                "• Использовано сегодня: " + user.getDailyRequestsUsed() + "/10\n" +
                "• Всего запросов: " + user.getTotalRequests() + "\n" +
                "• Баланс: $" + String.format("%.2f", user.getBalance()) + "\n" +
                "• Тариф: " + (user.getPlan().equals("PREMIUM") ? "Премиум" : "Бесплатный") + "\n\n" +
                "💳 Пополнить баланс: /payment\n" +
                "🆘 Помощь: /help";
    }

    /**
     * Проверяет, может ли пользователь сделать запрос
     */
    public boolean canUserMakeRequest(Long chatId) {
        Optional<User> userOpt = userRepository.findById(chatId);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        user.resetDailyLimitIfNeeded();

        // Пользователь может сделать запрос если:
        // 1. Не превышен дневной лимит ИЛИ
        // 2. Есть положительный баланс
        return user.getDailyRequestsUsed() < 10 || user.getBalance() > 0;
    }
}