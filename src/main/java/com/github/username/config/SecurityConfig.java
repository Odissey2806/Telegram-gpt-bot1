package com.github.username.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Конфигурация безопасности Spring Security
 * Настраивает доступ к эндпоинтам приложения
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Конфигурирует цепочку фильтров безопасности
     * В демо-версии отключаем security для упрощения
     * В продакшне обязательно настроить правильную безопасность!
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Отключаем CSRF защиту для упрощения (в продакшне включить!)
                .csrf(csrf -> csrf.disable())
                // Разрешаем все запросы (в продакшне настроить ограничения!)
                .authorizeHttpRequests(authz -> authz
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}