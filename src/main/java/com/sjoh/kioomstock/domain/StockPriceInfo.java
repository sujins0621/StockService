package com.sjoh.kioomstock.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_price_info", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"stockCode", "time"})
})
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
    private LocalDateTime time;     // 체결시간 (cntr_tm)

    private long currentPrice;      // 현재가 (cur_prc)

    private long diffFromPrev;      // 전일대비 (pred_pre)

    private String diffFromPrevSign; // 전일대비기호 (pred_pre_sig)

    private double fluctuationRate; // 등락율 (flu_rt)

    private long volume;            // 거래량 (trde_qty)

    private long accumulatedTradePrice; // 누적거래대금 (acc_trde_prica)

    private long accumulatedTradeVolume; // 누적거래량 (acc_trde_qty)

    private double volumePower;     // 체결강도 (cntr_str)

    private double volumePower5Min; // 체결강도5분 (cntr_str_5min)

    private double volumePower20Min; // 체결강도20분 (cntr_str_20min)

    private double volumePower60Min; // 체결강도60분 (cntr_str_60min)

    private String exchangeType;    // 거래소구분 (stex_tp)
}
