package com.sjoh.kioomstock.repository;

import com.sjoh.kioomstock.domain.StockDailyCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface StockDailyCandleRepository extends JpaRepository<StockDailyCandle, Long> {
    Optional<StockDailyCandle> findByStockCodeAndDate(String stockCode, LocalDate date);
}
