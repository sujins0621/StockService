package com.sjoh.kioomstock.service;

import com.sjoh.kioomstock.domain.StockPriceInfo;
import com.sjoh.kioomstock.repository.StockPriceInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class StockDataService {

    private static final Logger logger = LoggerFactory.getLogger(StockDataService.class);

    private final WebClient webClient;
    private final KiwoomAuthService authService;
    private final StockPriceInfoRepository stockPriceInfoRepository;

    // 모니터링할 종목 리스트 (예: 삼성전자 005930)
    // 실제로는 DB나 설정 파일에서 관리하는 것이 좋습니다.
    private final List<String> targetStockCodes = List.of("005930");

    public StockDataService(WebClient webClient, KiwoomAuthService authService, StockPriceInfoRepository stockPriceInfoRepository) {
        this.webClient = webClient;
        this.authService = authService;
        this.stockPriceInfoRepository = stockPriceInfoRepository;
    }

    // 평일 09:00 ~ 15:30 사이에 1분마다 실행 (장 운영 시간)
    @Scheduled(cron = "0 * 9-15 * * MON-FRI")
    public void collectStockData() {
        logger.info("Starting scheduled stock data collection...");

        authService.getAccessToken()
                .flatMap(token -> {
                    // 모든 타겟 종목에 대해 순차적으로 데이터 요청
                    // (API 호출 제한 고려 필요)
                    for (String code : targetStockCodes) {
                        fetchStockPrice(token, code).subscribe(
                                info -> {
                                    logger.info("Collected data for {}: {}", code, info);
                                    saveData(info);
                                },
                                error -> logger.error("Error collecting data for {}", code, error)
                        );
                    }
                    return Mono.empty();
                })
                .subscribe();
    }

    private Mono<StockPriceInfo> fetchStockPrice(String token, String stockCode) {
        // 키움 REST API 주식 현재가 시세 조회 엔드포인트 (가이드 참조 필요)
        // 예시 URL 구조입니다. 실제 API 문서에 따라 uri와 파라미터를 수정해야 합니다.
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price") // 예시 경로
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .build())
                .header("Authorization", "Bearer " + token)
                .header("tr_id", "FHKST01010100") // 예시 TR ID
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> parseResponse(stockCode, response));
    }

    private StockPriceInfo parseResponse(String stockCode, Map<String, Object> response) {
        // 응답 JSON 파싱 로직 (실제 API 응답 구조에 맞춰 수정 필요)
        // 여기서는 예시로 output 필드 내에 데이터가 있다고 가정합니다.
        Map<String, String> output = (Map<String, String>) response.get("output");

        if (output == null) {
            throw new RuntimeException("Invalid API response for " + stockCode);
        }

        // 예시 필드명 매핑 (실제 키움 API 문서 확인 필요)
        // 체결강도, 매수잔량, 매도잔량, 거래량, 현재가, 시가
        double volumePower = Double.parseDouble(output.getOrDefault("stck_prpr", "0")); // 체결강도 (API 필드명 확인 필요)
        long buyRemain = Long.parseLong(output.getOrDefault("total_bid_remn", "0")); // 매수잔량 (API 필드명 확인 필요)
        long sellRemain = Long.parseLong(output.getOrDefault("total_ask_remn", "0")); // 매도잔량 (API 필드명 확인 필요)
        long volume = Long.parseLong(output.getOrDefault("acml_vol", "0")); // 거래량
        long currentPrice = Long.parseLong(output.getOrDefault("stck_prpr", "0")); // 현재가
        long openPrice = Long.parseLong(output.getOrDefault("stck_oprc", "0")); // 시가

        return StockPriceInfo.builder()
                .stockCode(stockCode)
                .time(LocalDateTime.now())
                .volumePower(volumePower)
                .buyRemain(buyRemain)
                .sellRemain(sellRemain)
                .volume(volume)
                .currentPrice(currentPrice)
                .openPrice(openPrice)
                .build();
    }

    private void saveData(StockPriceInfo info) {
        stockPriceInfoRepository.save(info);
    }
}
