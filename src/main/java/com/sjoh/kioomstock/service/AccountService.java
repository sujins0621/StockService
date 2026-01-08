package com.sjoh.kioomstock.service;

import com.sjoh.kioomstock.domain.AccountInfo;
import com.sjoh.kioomstock.domain.AccountStockInfo;
import com.sjoh.kioomstock.repository.AccountInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    private final WebClient webClient;
    private final KiwoomAuthService authService;
    private final AccountInfoRepository accountInfoRepository;

    public AccountService(WebClient webClient, KiwoomAuthService authService, AccountInfoRepository accountInfoRepository) {
        this.webClient = webClient;
        this.authService = authService;
        this.accountInfoRepository = accountInfoRepository;
    }

    // 서버 시작 시 1회 실행
    @EventListener(ApplicationReadyEvent.class)
    public void initAccountInfo() {
        logger.info("Initializing account info...");
        fetchAndSaveAccountInfo().subscribe(
                info -> logger.info("Successfully initialized account info for: {}", info.getAccountName()),
                error -> logger.error("Failed to initialize account info", error)
        );
    }

    public Mono<AccountInfo> fetchAndSaveAccountInfo() {
        return authService.getAccessToken()
                .flatMap(this::fetchAccountInfo)
                .map(this::saveAccountInfo);
    }

    private Mono<AccountInfo> fetchAccountInfo(String token) {
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("qry_tp", "0"); // 0:전체
        requestBody.put("dmst_stex_tp", "KRX"); // KRX:한국거래소

        return webClient.post()
                .uri("/api/dostk/acnt")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("api-id", "kt00004")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    logger.info("Account API Response: {}", response);
                    return parseResponse(response);
                });
    }

    @SuppressWarnings("unchecked")
    private AccountInfo parseResponse(Map<String, Object> response) {
        try {
            Map<String, Object> data = response;

            AccountInfo accountInfo = AccountInfo.builder()
                    .accountName(getString(data, "acnt_nm"))
                    .branchName(getString(data, "brch_nm"))
                    .deposit(parseLong(data, "entr"))
                    .d2Deposit(parseLong(data, "d2_entra"))
                    .totalEvalAmount(parseLong(data, "tot_est_amt"))
                    .assetEvalAmount(parseLong(data, "aset_evlt_amt"))
                    .totalPurchaseAmount(parseLong(data, "tot_pur_amt"))
                    .estimatedDepositAsset(parseLong(data, "prsm_dpst_aset_amt"))
                    .totalLoanAmount(parseLong(data, "tot_grnt_sella"))
                    .todayInvestPrincipal(parseLong(data, "tdy_lspft_amt"))
                    .monthInvestPrincipal(parseLong(data, "invt_bsamt"))
                    .accumInvestPrincipal(parseLong(data, "lspft_amt"))
                    .todayProfitLoss(parseLong(data, "tdy_lspft"))
                    .monthProfitLoss(parseLong(data, "lspft2"))
                    .accumProfitLoss(parseLong(data, "lspft"))
                    .todayProfitRate(parseDouble(data, "tdy_lspft_rt"))
                    .monthProfitRate(parseDouble(data, "lspft_ratio"))
                    .accumProfitRate(parseDouble(data, "lspft_rt"))
                    .build();

            List<Map<String, Object>> stockList = (List<Map<String, Object>>) data.get("stk_acnt_evlt_prst");
            if (stockList != null) {
                List<AccountStockInfo> stockInfos = new ArrayList<>();
                for (Map<String, Object> stockData : stockList) {
                    AccountStockInfo stockInfo = AccountStockInfo.builder()
                            .accountInfo(accountInfo)
                            .stockCode(getString(stockData, "stk_cd"))
                            .stockName(getString(stockData, "stk_nm"))
                            .remainQty(parseLong(stockData, "rmnd_qty"))
                            .avgPrice(parseDouble(stockData, "avg_prc"))
                            .currentPrice(parseLong(stockData, "cur_prc"))
                            .evalAmount(parseLong(stockData, "evlt_amt"))
                            .profitLossAmount(parseLong(stockData, "pl_amt"))
                            .profitLossRate(parseDouble(stockData, "pl_rt"))
                            .loanDate(getString(stockData, "loan_dt"))
                            .purchaseAmount(parseLong(stockData, "pur_amt"))
                            .settlementRemain(parseLong(stockData, "setl_remn"))
                            .prevBuyQty(parseLong(stockData, "pred_buyq"))
                            .prevSellQty(parseLong(stockData, "pred_sellq"))
                            .todayBuyQty(parseLong(stockData, "tdy_buyq"))
                            .todaySellQty(parseLong(stockData, "tdy_sellq"))
                            .build();
                    stockInfos.add(stockInfo);
                }
                accountInfo.setStockInfos(stockInfos);
            }

            return accountInfo;

        } catch (Exception e) {
            logger.error("Error parsing account info response", e);
            throw new RuntimeException("Failed to parse account info", e);
        }
    }

    private AccountInfo saveAccountInfo(AccountInfo accountInfo) {
        accountInfoRepository.deleteAll();
        return accountInfoRepository.save(accountInfo);
    }

    private String getString(Map<String, Object> data, String key) {
        return data.getOrDefault(key, "").toString();
    }

    private long parseLong(Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val == null) return 0;
        try {
            return Long.parseLong(val.toString().replace("+", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseDouble(Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val == null) return 0.0;
        try {
            return Double.parseDouble(val.toString().replace("+", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
