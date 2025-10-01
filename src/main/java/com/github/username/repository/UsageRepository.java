package com.github.username.repository;

import com.github.username.entity.UsageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для истории использования
 */
@Repository
public interface UsageRepository extends JpaRepository<UsageHistory, Long> {
    // Базовые методы уже включены через JpaRepository
}