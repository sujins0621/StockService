package com.sjoh.kioomstock.repository;

import com.sjoh.kioomstock.domain.StockOrderBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockOrderBookRepository extends JpaRepository<StockOrderBook, Long> {
}
