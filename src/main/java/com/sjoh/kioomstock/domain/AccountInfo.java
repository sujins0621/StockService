package com.sjoh.kioomstock.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "account_info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String accountName; // acnt_nm
    private String branchName; // brch_nm
    private long deposit; // entr 예수금
    private long d2Deposit; // d2_entra D+2추정예수금
    private long totalEvalAmount; // tot_est_amt 유가잔고평가액
    private long assetEvalAmount; // aset_evlt_amt 예탁자산평가액
    private long totalPurchaseAmount; // tot_pur_amt 총매입금액
    private long estimatedDepositAsset; // prsm_dpst_aset_amt 추정예탁자산
    private long totalLoanAmount; // tot_grnt_sella 매도담보대출금
    
    private long todayInvestPrincipal; // tdy_lspft_amt 당일투자원금
    private long monthInvestPrincipal; // invt_bsamt 당월투자원금
    private long accumInvestPrincipal; // lspft_amt 누적투자원금
    
    private long todayProfitLoss; // tdy_lspft 당일투자손익
    private long monthProfitLoss; // lspft2 당월투자손익
    private long accumProfitLoss; // lspft 누적투자손익
    
    private double todayProfitRate; // tdy_lspft_rt 당일손익율
    private double monthProfitRate; // lspft_ratio 당월손익율
    private double accumProfitRate; // lspft_rt 누적손익율

    @OneToMany(mappedBy = "accountInfo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<AccountStockInfo> stockInfos = new ArrayList<>();
}
