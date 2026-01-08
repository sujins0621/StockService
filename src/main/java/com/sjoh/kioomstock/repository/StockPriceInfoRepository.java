package com.sjoh.kioomstock.repository;

import com.sjoh.kioomstock.domain.StockPriceInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockPriceInfoRepository extends JpaRepository<StockPriceInfo, Long> {
}
