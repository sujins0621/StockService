package com.sjoh.kioomstock.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_order_book")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockOrderBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String stockCode;       // 종목코드

    @Column(nullable = false)
    private LocalDateTime time;     // 호가잔량기준시간 (bid_req_base_tm)

    private long totalSellRemain;   // 총매도잔량 (tot_sel_req)
    private long totalBuyRemain;    // 총매수잔량 (tot_buy_req)
}
