package com.github.username.service;

import com.github.username.entity.User;
import com.github.username.entity.UsageHistory;
import com.github.username.repository.UserRepository;
import com.github.username.repository.UsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {
    private final UserRepository userRepository;
    private final UsageRepository usageRepository;
    private final OpenAIService openAIService;
    private final PaymentService paymentService;
    private final TelegramMessageService messageService;

    @Value("${usage.daily.free:10}")
    private int dailyFreeLimit;

    @Transactional
    public void processMessage(Long chatId, String messageText, String firstName, String lastName, String username) {
        // Найти или создать пользователя
        User user = userRepository.findById(chatId)
                .orElseGet(() -> createNewUser(chatId, firstName, lastName, username));

        // Обновить активность
        user.setLastActivity(LocalDateTime.now());

        // Проверить лимиты
        user.resetDailyLimitIfNeeded();

        if (user.getDailyRequestsUsed() >= dailyFreeLimit && user.getBalance() <= 0) {
            String limitMessage = "❌ Вы использовали все бесплатные запросы на сегодня (" + dailyFreeLimit + ").\n\n" +
                    "💳 Чтобы продолжить, пополните баланс:\n" +
                    "• 10 запросов - 1$\n" +
                    "• 50 запросов - 4$\n" +
                    "• 100 запросов - 7$\n\n" +
                    "Для пополнения используйте команду /payment";

            messageService.sendMessage(chatId, limitMessage);
            return;
        }

        // Отправляем сообщение "обрабатывается"
        messageService.sendMessage(chatId, "⏳ Обрабатываю ваш запрос...");

        // Получить ответ от OpenAI
        String response;
        try {
            response = openAIService.getChatResponse(messageText);

            // Записать использование
            UsageHistory usage = new UsageHistory();
            usage.setUser(user);
            usage.setMessage(messageText);
            usage.setResponse(response);
            usage.setTokensUsed(response.length() / 4);
            usage.setCost(0.0);

            usageRepository.save(usage);

            // Обновить счетчики
            user.setDailyRequestsUsed(user.getDailyRequestsUsed() + 1);
            user.setTotalRequests(user.getTotalRequests() + 1);
            user.setLastRequestDate(LocalDateTime.now());

            userRepository.save(user);

        } catch (Exception e) {
            log.error("Error processing message", e);
            response = "⚠️ Произошла ошибка при обработке запроса. Пожалуйста, попробуйте позже.";
        }

        // Отправляем ответ пользователю
        messageService.sendMessage(chatId, response);
    }

    private User createNewUser(Long chatId, String firstName, String lastName, String username) {
        User user = new User();
        user.setChatId(chatId);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUsername(username);
        user.setPlan("FREE");
        user.setDailyRequestsUsed(0);
        user.setTotalRequests(0);
        user.setBalance(0.0);

        return userRepository.save(user);
    }

    public String getUserStats(Long chatId) {
        Optional<User> userOpt = userRepository.findById(chatId);
        if (userOpt.isEmpty()) {
            return "Пользователь не найден";
        }

        User user = userOpt.get();
        user.resetDailyLimitIfNeeded(); // Обновить лимиты перед показом

        return "📊 Ваша статистика:\n\n" +
                "• Использовано сегодня: " + user.getDailyRequestsUsed() + "/" + dailyFreeLimit + "\n" +
                "• Всего запросов: " + user.getTotalRequests() + "\n" +
                "• Баланс: $" + String.format("%.2f", user.getBalance()) + "\n" +
                "• Тариф: " + (user.getPlan().equals("PREMIUM") ? "Премиум" : "Бесплатный") + "\n\n" +
                "💳 Пополнить баланс: /payment\n" +
                "🆘 Помощь: /help";
    }
}
