package com.github.username.repository;

import com.github.username.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для платежей
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    // Можно добавить кастомные методы, например:
    // List<Payment> findByUserIdAndStatus(Long userId, String status);
}