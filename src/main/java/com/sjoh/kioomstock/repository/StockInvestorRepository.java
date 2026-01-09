package com.sjoh.kioomstock.repository;

import com.sjoh.kioomstock.domain.StockInvestor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface StockInvestorRepository extends JpaRepository<StockInvestor, Long> {
    Optional<StockInvestor> findByStockCodeAndDate(String stockCode, LocalDate date);
}
