package com.sjoh.kioomstock.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "stock_daily_candle", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"stockCode", "date"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDailyCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String stockCode;       // 종목코드

    @Column(nullable = false)
    private LocalDate date;         // 일자 (dt)

    private long closePrice;        // 현재가/종가 (cur_prc)
    private long openPrice;         // 시가 (open_pric)
    private long highPrice;         // 고가 (high_pric)
    private long lowPrice;          // 저가 (low_pric)
    private long volume;            // 거래량 (trde_qty)
    private long tradingValue;      // 거래대금 (trde_prica)
    private long changeFromPrev;    // 전일대비 (pred_pre)
    private String changeSign;      // 전일대비기호 (pred_pre_sig)
    private double turnoverRate;    // 거래회전율 (trde_tern_rt)
}
