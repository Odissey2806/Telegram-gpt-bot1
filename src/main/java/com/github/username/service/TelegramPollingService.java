package com.github.username.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.SuccessfulPayment;
import com.pengrad.telegrambot.model.PreCheckoutQuery;
import com.pengrad.telegrambot.request.AnswerPreCheckoutQuery;
import com.pengrad.telegrambot.request.DeleteWebhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPollingService {

    private final TelegramBot bot;
    private final TelegramBotService botService;
    private final TelegramMessageService messageService;
    private final TelegramPaymentService paymentService;

    @PostConstruct
    public void init() {
        log.info("🚀 Запуск Telegram Polling Service...");

        try {
            var deleteWebhookRequest = new DeleteWebhook();
            var response = bot.execute(deleteWebhookRequest);

            if (response.isOk()) {
                log.info("✅ Webhook успешно удален, переходим в polling режим");
            } else {
                log.warn("⚠️ Не удалось удалить webhook: {}", response.description());
            }
        } catch (Exception e) {
            log.info("ℹ️ Webhook не был установлен или произошла ошибка: {}", e.getMessage());
        }

        bot.setUpdatesListener(new UpdatesListener() {
            @Override
            public int process(List<Update> updates) {
                for (Update update : updates) {
                    processUpdate(update);
                }
                return UpdatesListener.CONFIRMED_UPDATES_ALL;
            }
        });

        log.info("✅ Telegram Polling Service успешно запущен!");
    }

    private void processUpdate(Update update) {
        try {
            if (update.message() != null && update.message().text() != null) {
                handleTextMessage(update.message());
            }

            if (update.preCheckoutQuery() != null) {
                handlePreCheckoutQuery(update.preCheckoutQuery());
            }

            if (update.message() != null && update.message().successfulPayment() != null) {
                handleSuccessfulPayment(update.message());
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке обновления", e);
        }
    }

    private void handleTextMessage(Message message) {
        Long chatId = message.chat().id();
        String text = message.text();
        String firstName = message.chat().firstName();
        String lastName = message.chat().lastName() != null ? message.chat().lastName() : "";
        String username = message.chat().username() != null ? message.chat().username() : "";

        log.info("📨 Получено сообщение от {} ({}): {}", firstName, chatId, text);

        if (text.startsWith("/")) {
            handleCommand(chatId, text, firstName);
        } else {
            handleTextMessage(chatId, text, firstName, lastName, username);
        }
    }

    private void handleCommand(Long chatId, String command, String firstName) {
        log.info("🔧 Обработка команды: {} от пользователя {}", command, chatId);

        switch (command) {
            case "/start":
                handleStartCommand(chatId, firstName);
                break;

            case "/stats":
                handleStatsCommand(chatId);
                break;

            case "/payment":
                handlePaymentCommand(chatId);
                break;

            case "/buy_10":
            case "/buy_50":
            case "/buy_100":
                handleBuyCommand(chatId, command.substring(5));
                break;

            case "/help":
                handleHelpCommand(chatId);
                break;

            default:
                handleUnknownCommand(chatId);
        }
    }

    private void handleStartCommand(Long chatId, String firstName) {
        String welcomeMessage = "🤖 Добро пожаловать, " + firstName + "!\n\n" +
                "Я - AI помощник на основе ChatGPT. Задавайте мне любые вопросы!\n\n" +
                "📊 Статистика: /stats\n" +
                "💳 Пополнить баланс: /payment\n" +
                "🆘 Помощь: /help\n\n" +
                "🎯 Сегодня у вас **10 бесплатных запросов**!\n" +
                "💎 После лимита - докупайте дополнительные запросы";

        messageService.sendMessage(chatId, welcomeMessage);
        log.info("✅ Приветствие отправлено пользователю {}", chatId);
    }

    private void handleStatsCommand(Long chatId) {
        String stats = botService.getUserStats(chatId);
        messageService.sendMessage(chatId, stats);
        log.info("📊 Статистика отправлена пользователю {}", chatId);
    }

    private void handlePaymentCommand(Long chatId) {
        String paymentInfo = "💳 **Доступные пакеты запросов:**\n\n" +
                "🔹 **10 запросов** - 100 руб.\n" +
                "   Команда: /buy_10\n\n" +
                "🔹 **50 запросов** - 400 руб. (экономия 100 руб.)\n" +
                "   Команда: /buy_50\n\n" +
                "🔹 **100 запросов** - 700 руб. (экономия 300 руб.)\n" +
                "   Команда: /buy_100\n\n" +
                "💎 **Почему выгоднее покупать больше?**\n" +
                "• 10 запросов: 10 руб./запрос\n" +
                "• 50 запросов: 8 руб./запрос\n" +
                "• 100 запросов: 7 руб./запрос\n\n" +
                "⚡ После оплаты запросы автоматически добавятся к вашему балансу!";

        messageService.sendMessage(chatId, paymentInfo);
        log.info("💳 Информация об оплате отправлена пользователю {}", chatId);
    }

    private void handleBuyCommand(Long chatId, String packageType) {
        log.info("🛒 Запрос на покупку от пользователя {}: пакет {}", chatId, packageType);

        if (!isValidPackageType(packageType)) {
            log.warn("⚠️ Пользователь {} запросил невалидный пакет: {}", chatId, packageType);
            messageService.sendMessage(chatId,
                    "❌ Неверный тип пакета. Используйте /buy_10, /buy_50 или /buy_100");
            return;
        }

        String packageInfo = getPackageDisplayInfo(packageType);
        messageService.sendMessage(chatId,
                "💳 **Подготовка счета для оплаты...**\n\n" + packageInfo);

        if (paymentService.sendInvoice(chatId, packageType)) {
            log.info("✅ Счет для пакета {} отправлен пользователю {}", packageType, chatId);

            String instructionMessage = "📋 **Инструкция по оплате (ТЕСТОВЫЙ РЕЖИМ):**\n\n" +
                    "1. 🪟 Откроется окно оплаты Telegram\n" +
                    "2. 💳 Введите данные **тестовой карты**:\n" +
                    "   • Номер: `4111 1111 1111 1111`\n" +
                    "   • Срок: любая будущая дата\n" +
                    "   • CVV: любые 3 цифры\n" +
                    "3. ✅ Подтвердите оплату\n\n" +
                    "💡 **Важно:** Это тестовый платеж, реальные деньги **не списываются**!\n" +
                    "🔒 Все платежи защищены Telegram Payments";

            messageService.sendMessage(chatId, instructionMessage);

        } else {
            log.error("❌ Не удалось отправить счет пользователю {} для пакета {}", chatId, packageType);

            String errorMessage = "❌ **Не удалось создать счет для оплаты**\n\n" +
                    "Возможные причины:\n" +
                    "• Платежная система временно недоступна\n" +
                    "• Технические работы\n" +
                    "• Проблемы с подключением\n\n" +
                    "🔄 Пожалуйста, попробуйте позже\n" +
                    "🔧 Или обратитесь в поддержку";

            messageService.sendMessage(chatId, errorMessage);
        }
    }

    private void handleHelpCommand(Long chatId) {
        String helpMessage = "🆘 **Помощь по боту:**\n\n" +
                "🤖 **Основные функции:**\n" +
                "• Просто напишите вопрос - и я отвечу!\n" +
                "• Бесплатно: 10 запросов в день\n" +
                "• После лимита - докупайте дополнительные запросы\n\n" +
                "💎 **Система оплаты:**\n" +
                "• Оплата через Telegram Payments\n" +
                "• Безопасно и удобно\n" +
                "• Моментальное пополнение баланса\n\n" +
                "📋 **Доступные команды:**\n" +
                "/start - начать работу\n" +
                "/stats - ваша статистика\n" +
                "/payment - информация об оплате\n" +
                "/buy_10 - купить 10 запросов (100 руб.)\n" +
                "/buy_50 - купить 50 запросов (400 руб.)\n" +
                "/buy_100 - купить 100 запросов (700 руб.)\n" +
                "/help - эта справка";

        messageService.sendMessage(chatId, helpMessage);
        log.info("🆘 Справка отправлена пользователю {}", chatId);
    }

    private void handleUnknownCommand(Long chatId) {
        messageService.sendMessage(chatId,
                "❌ Неизвестная команда. Используйте /help для просмотра доступных команд.");
        log.warn("⚠️ Пользователь {} отправил неизвестную команду", chatId);
    }

    /**
     * Обрабатывает предварительные запросы оплаты (pre-checkout)
     */
    private void handlePreCheckoutQuery(PreCheckoutQuery preCheckoutQuery) {
        try {
            Long userId = preCheckoutQuery.from().id();
            String payload = preCheckoutQuery.invoicePayload();

            log.info("🔄 Pre-checkout запрос от пользователя {}: payload {}", userId, payload);

            // ✅ ВАРИАНТ 1: Просто создаем объект - возможно он по умолчанию подтверждает запрос
            AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery(preCheckoutQuery.id());
            bot.execute(answer);

            log.info("✅ Pre-checkout запрос подтвержден для пользователя {}", userId);

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке pre-checkout запроса", e);

            try {
                // ✅ ВАРИАНТ 2: Просто создаем объект без дополнительных параметров
                AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery(preCheckoutQuery.id());
                bot.execute(answer);
                log.warn("⚠️ Pre-checkout запрос обработан с ошибкой для пользователя {}", preCheckoutQuery.from().id());

            } catch (Exception ex) {
                log.error("❌ Ошибка при обработке pre-checkout запроса с ошибкой", ex);
            }
        }
    }

    /**
     * Обрабатывает успешные платежи
     * Вызывается когда платеж успешно завершен
     */
    private void handleSuccessfulPayment(Message message) {
        try {
            SuccessfulPayment payment = message.successfulPayment();
            Long chatId = message.chat().id();
            String payload = payment.invoicePayload();
            String currency = payment.currency();
            int totalAmount = payment.totalAmount();

            log.info("💰 Успешный платеж от пользователя {}: {} {}, payload {}",
                    chatId, totalAmount / 100, currency, payload);

            // Обрабатываем успешный платеж
            paymentService.handleSuccessfulPayment(payload, chatId);

            // Отправляем подтверждение пользователю
            String thankYouMessage = "✅ **Оплата прошла успешно!** 🎉\n\n" +
                    "💳 Сумма: " + (totalAmount / 100) + " " + currency + "\n" +
                    "📦 Запросы добавлены к вашему балансу!\n\n" +
                    "💰 Теперь вы можете продолжать общение с ботом!\n" +
                    "📊 Посмотреть баланс: /stats\n\n" +
                    "🙏 Спасибо за доверие!";

            messageService.sendMessage(chatId, thankYouMessage);
            log.info("✅ Подтверждение платежа отправлено пользователю {}", chatId);

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке успешного платежа", e);
        }
    }

    private void handleTextMessage(Long chatId, String text, String firstName, String lastName, String username) {
        new Thread(() -> {
            try {
                log.info("🧠 Обработка AI запроса от {}: {}", chatId, text);
                botService.processMessage(chatId, text, firstName, lastName, username);

            } catch (Exception e) {
                log.error("❌ Ошибка при обработке сообщения от {}", chatId, e);
                messageService.sendMessage(chatId,
                        "⚠️ Произошла ошибка при обработке вашего запроса. Пожалуйста, попробуйте позже.");
            }
        }).start();
    }

    private boolean isValidPackageType(String packageType) {
        return packageType.equals("10") || packageType.equals("50") || packageType.equals("100");
    }

    private String getPackageDisplayInfo(String packageType) {
        switch (packageType) {
            case "10":
                return "🔹 **Пакет: 10 запросов**\n" +
                        "💵 Стоимость: 100 руб.\n" +
                        "📊 Цена за запрос: 10 руб.\n" +
                        "⏱ Время обработки: мгновенно";
            case "50":
                return "🔹 **Пакет: 50 запросов**\n" +
                        "💵 Стоимость: 400 руб.\n" +
                        "📊 Цена за запрос: 8 руб. (экономия 20%!)\n" +
                        "💰 Экономия: 100 руб.\n" +
                        "⏱ Время обработки: мгновенно";
            case "100":
                return "🔹 **Пакет: 100 запросов**\n" +
                        "💵 Стоимость: 700 руб.\n" +
                        "📊 Цена за запрос: 7 руб. (экономия 30%!)\n" +
                        "💰 Экономия: 300 руб.\n" +
                        "⏱ Время обработки: мгновенно";
            default:
                return "❌ Неизвестный пакет";
        }
    }
}