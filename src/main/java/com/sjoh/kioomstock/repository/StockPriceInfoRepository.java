package com.sjoh.kioomstock.repository;

import com.sjoh.kioomstock.domain.StockPriceInfo;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockPriceInfoRepository extends JpaRepository<StockPriceInfo, Long> {
    Optional<StockPriceInfo> findByStockCodeAndTime(String stockCode, LocalDateTime time);
    List<StockPriceInfo> findByStockCode(String stockCode, Sort sort);
}
