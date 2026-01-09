package com.sjoh.kioomstock.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_investor")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockInvestor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime time;     // 수집 시간 (time) - StockPriceInfo와 동일한 타입

    @Column(nullable = false)
    private String stockCode;       // 종목코드

    @Column(nullable = false)
    private LocalDate date;         // 일자 (dt)

    private long currentPrice;      // 현재가 (cur_prc)
    private long changeFromPrev;    // 전일대비 (pred_pre)
    private double fluctuationRate; // 등락율 (flu_rt)
    private long volume;            // 누적거래량 (acc_trde_qty)
    private long tradingValue;      // 누적거래대금 (acc_trde_prica)

    private long individual;        // 개인투자자 (ind_invsr)
    private long foreigner;         // 외국인투자자 (frgnr_invsr)
    private long institution;       // 기관계 (orgn)
    private long financialInvestment; // 금융투자 (fnnc_invt)
    private long insurance;         // 보험 (insrnc)
    private long investmentTrust;   // 투신 (invtrt)
    private long etcFinance;        // 기타금융 (etc_fnnc)
    private long bank;              // 은행 (bank)
    private long pensionFund;       // 연기금등 (penfnd_etc)
    private long privateFund;       // 사모펀드 (samo_fund)
    private long nation;            // 국가 (natn)
    private long etcCorp;           // 기타법인 (etc_corp)
    private long foreignNational;   // 내외국인 (natfor)
}
