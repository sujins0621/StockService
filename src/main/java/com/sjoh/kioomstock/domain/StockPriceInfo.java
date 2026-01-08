package com.sjoh.kioomstock.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_price_info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPriceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String stockCode;       // 종목코드

    @Column(nullable = false)
    private LocalDateTime time;     // 시간

    private double volumePower;     // 체결강도

    private long buyRemain;         // 매수잔량

    private long sellRemain;        // 매도잔량

    private long volume;            // 거래량

    private long currentPrice;      // 현재가

    private long openPrice;         // 시가
}
