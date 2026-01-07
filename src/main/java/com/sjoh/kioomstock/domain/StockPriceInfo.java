package com.sjoh.kioomstock.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StockPriceInfo {
    private String stockCode;       // 종목코드
    private LocalDateTime time;     // 시간
    private double volumePower;     // 체결강도
    private long volume;            // 거래량
    private long currentPrice;      // 현재가
}
