package com.sjoh.kioomstock.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "account_stock_info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStockInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_info_id")
    @JsonIgnore
    private AccountInfo accountInfo;

    private String stockCode; // stk_cd
    private String stockName; // stk_nm
    private long remainQty; // rmnd_qty 보유수량
    private double avgPrice; // avg_prc 평균단가
    private long currentPrice; // cur_prc 현재가
    private long evalAmount; // evlt_amt 평가금액
    private long profitLossAmount; // pl_amt 손익금액
    private double profitLossRate; // pl_rt 손익율
    private String loanDate; // loan_dt 대출일
    private long purchaseAmount; // pur_amt 매입금액
    private long settlementRemain; // setl_remn 결제잔고
    private long prevBuyQty; // pred_buyq 전일매수수량
    private long prevSellQty; // pred_sellq 전일매도수량
    private long todayBuyQty; // tdy_buyq 금일매수수량
    private long todaySellQty; // tdy_sellq 금일매도수량
}
