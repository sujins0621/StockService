package com.sjoh.kioomstock.service;

import com.sjoh.kioomstock.domain.StockPriceInfo;
import com.sjoh.kioomstock.repository.StockPriceInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockDataService {

    private static final Logger logger = LoggerFactory.getLogger(StockDataService.class);

    private final WebClient webClient;
    private final KiwoomAuthService authService;
    private final StockPriceInfoRepository stockPriceInfoRepository;

    // 모니터링할 종목 리스트 (예: 삼성전자 005930)
    private final List<String> targetStockCodes = List.of("005930");

    public StockDataService(WebClient webClient, KiwoomAuthService authService, StockPriceInfoRepository stockPriceInfoRepository) {
        this.webClient = webClient;
        this.authService = authService;
        this.stockPriceInfoRepository = stockPriceInfoRepository;
    }

    // 평일 09:00 ~ 15:30 사이에 1분마다 실행 (장 운영 시간)
    // 테스트를 위해 cron 표현식을 매 분마다 실행되도록 수정하거나, 현재 시간이 범위 내인지 확인 필요
    @Scheduled(cron = "0 * * * * *") // 테스트를 위해 시간 제한 제거 (매 분 실행)
    public void collectStockData() {
        logger.info("Starting scheduled stock data collection...");

        authService.getAccessToken()
                .flatMapMany(token -> Flux.fromIterable(targetStockCodes)
                        .flatMap(code -> fetchStockPrice(token, code)
                                .doOnNext(infoList -> {
                                    if (infoList != null && !infoList.isEmpty()) {
                                        logger.info("Collected {} data points for {}", infoList.size(), code);
                                        saveData(infoList);
                                    }
                                })
                                .onErrorResume(error -> {
                                    logger.error("Error collecting data for {}", code, error);
                                    return Mono.empty();
                                })
                        )
                )
                .subscribe();
    }

    private Mono<List<StockPriceInfo>> fetchStockPrice(String token, String stockCode) {
        logger.info("fetchStockPrice CALL for {}", stockCode);

        // 요청 바디 생성
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("stk_cd", stockCode);
        // 필요 시 추가 파라미터 설정 (예: 조회 시간 등)

        // 키움 REST API 체결강도 추이 시간별 요청 (ka10046)
        return webClient.post()
                .uri("/api/dostk/mrkcond") // 예시 경로, 실제 경로 확인 필요
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("api-id", "ka10046") // TR ID 설정
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> parseResponse(stockCode, (Map<String, Object>) response))
                .onErrorResume(e -> {
                    logger.error("API call failed for {}: {}", stockCode, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    @SuppressWarnings("unchecked")
    private List<StockPriceInfo> parseResponse(String stockCode, Map<String, Object> response) {
        try {
            // 응답 구조: { "cntr_str_tm": [ { ... }, ... ] }
            List<Map<String, String>> chartData = (List<Map<String, String>>) response.get("cntr_str_tm");

            if (chartData == null || chartData.isEmpty()) {
                logger.warn("No chart data found for {}: {}", stockCode, response);
                return List.of();
            }

            // 1분마다 실행되므로 가장 최근 데이터 1건만 가져오거나, 전체를 가져와서 중복 체크 후 저장
            // 여기서는 가장 최근 데이터 1건만 처리하는 것으로 가정 (리스트의 첫 번째 요소가 최신이라고 가정)
            if (!chartData.isEmpty()) {
                return List.of(mapToStockPriceInfo(stockCode, chartData.get(0)));
            }
            return List.of();

        } catch (Exception e) {
            logger.error("Error parsing response for {}: {}", stockCode, e.getMessage());
            return List.of();
        }
    }

    private StockPriceInfo mapToStockPriceInfo(String stockCode, Map<String, String> data) {
        // 체결시간 파싱 (HHmmss 형식)
        LocalDateTime time = LocalDateTime.now();
        String timeStr = data.get("cntr_tm");
        if (timeStr != null && timeStr.length() == 6) {
            try {
                LocalTime localTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HHmmss"));
                time = LocalDateTime.of(LocalDate.now(), localTime);
            } catch (Exception e) {
                logger.warn("Failed to parse time: {}", timeStr);
            }
        }
        
        // 데이터 매핑 (숫자에 포함된 '+' 기호 제거 처리)
        long currentPrice = parseLong(data.get("cur_prc"));
        long diffFromPrev = parseLong(data.get("pred_pre"));
        String diffFromPrevSign = data.getOrDefault("pred_pre_sig", "");
        double fluctuationRate = parseDouble(data.get("flu_rt"));
        long volume = parseLong(data.get("trde_qty"));
        long accumulatedTradePrice = parseLong(data.get("acc_trde_prica"));
        long accumulatedTradeVolume = parseLong(data.get("acc_trde_qty"));
        double volumePower = parseDouble(data.get("cntr_str"));
        double volumePower5Min = parseDouble(data.get("cntr_str_5min"));
        double volumePower20Min = parseDouble(data.get("cntr_str_20min"));
        double volumePower60Min = parseDouble(data.get("cntr_str_60min"));
        String exchangeType = data.getOrDefault("stex_tp", "");

        return StockPriceInfo.builder()
                .stockCode(stockCode)
                .time(time)
                .currentPrice(currentPrice)
                .diffFromPrev(diffFromPrev)
                .diffFromPrevSign(diffFromPrevSign)
                .fluctuationRate(fluctuationRate)
                .volume(volume)
                .accumulatedTradePrice(accumulatedTradePrice)
                .accumulatedTradeVolume(accumulatedTradeVolume)
                .volumePower(volumePower)
                .volumePower5Min(volumePower5Min)
                .volumePower20Min(volumePower20Min)
                .volumePower60Min(volumePower60Min)
                .exchangeType(exchangeType)
                .build();
    }

    private long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        try {
            // '+' 기호 제거 및 공백 제거
            return Long.parseLong(value.replace("+", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) return 0.0;
        try {
            // '+' 기호 제거 및 공백 제거
            return Double.parseDouble(value.replace("+", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void saveData(List<StockPriceInfo> infoList) {
        stockPriceInfoRepository.saveAll(infoList);
    }
}
