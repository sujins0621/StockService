package com.sjoh.kioomstock.service;

import com.sjoh.kioomstock.domain.StockOrderBook;
import com.sjoh.kioomstock.domain.StockPriceInfo;
import com.sjoh.kioomstock.repository.StockOrderBookRepository;
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
    private final StockOrderBookRepository stockOrderBookRepository;

    // 모니터링할 종목 리스트 (예: 삼성전자 005930)
    private final List<String> targetStockCodes = List.of("005930");

    public StockDataService(WebClient webClient, KiwoomAuthService authService, StockPriceInfoRepository stockPriceInfoRepository, StockOrderBookRepository stockOrderBookRepository) {
        this.webClient = webClient;
        this.authService = authService;
        this.stockPriceInfoRepository = stockPriceInfoRepository;
        this.stockOrderBookRepository = stockOrderBookRepository;
    }

    // 평일 09:00 ~ 15:30 사이에 1분마다 실행 (장 운영 시간)
    // 테스트를 위해 cron 표현식을 매 분마다 실행되도록 수정하거나, 현재 시간이 범위 내인지 확인 필요
    @Scheduled(cron = "0 * * * * *") // 테스트를 위해 시간 제한 제거 (매 분 실행)
    public void collectStockData() {
        logger.info("Starting scheduled stock data collection...");

        authService.getAccessToken()
                .flatMapMany(token -> Flux.fromIterable(targetStockCodes)
                        .flatMap(code -> Mono.zip(
                                fetchStockPrice(token, code),
                                fetchOrderBook(token, code)
                        ).doOnNext(tuple -> {
                            List<StockPriceInfo> priceInfos = tuple.getT1();
                            StockOrderBook orderBook = tuple.getT2();

                            if (priceInfos != null && !priceInfos.isEmpty()) {
                                logger.info("Collected {} data points for {}", priceInfos.size(), code);
                                saveData(priceInfos);
                            }
                            if (orderBook != null) {
                                logger.info("Collected order book for {}: {}", code, orderBook);
                                saveOrderBook(orderBook);
                            }
                        }).onErrorResume(error -> {
                            logger.error("Error collecting data for {}", code, error);
                            return Mono.empty();
                        }))
                )
                .subscribe();
    }

    private Mono<List<StockPriceInfo>> fetchStockPrice(String token, String stockCode) {
        logger.info("fetchStockPrice CALL for {}", stockCode);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("stk_cd", stockCode);

        return webClient.post()
                .uri("/api/dostk/mrkcond")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("api-id", "ka10046")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    logger.info("API Response for {}: {}", stockCode, response);
                    return parseResponse(stockCode, (Map<String, Object>) response);
                })
                .onErrorResume(e -> {
                    logger.error("API call failed for {}: {}", stockCode, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private Mono<StockOrderBook> fetchOrderBook(String token, String stockCode) {
        logger.info("fetchOrderBook CALL for {}", stockCode);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("stk_cd", stockCode);

        return webClient.post()
                .uri("/api/dostk/mrkcond") // 주식 호가 잔량 조회 (가이드에 따라 경로 확인 필요, 여기선 mrkcond로 가정하나 실제론 다를 수 있음)
                // 가이드에 따르면 /api/dostk/mrkcond 가 맞는지 확인 필요. 
                // 이전 요청은 ka10046(체결강도)였고, 이번엔 ka10004(호가잔량)임.
                // 보통 같은 엔드포인트에 api-id로 구분하거나, 엔드포인트가 다를 수 있음.
                // 여기서는 가이드에 명시된 경로가 없으므로 이전과 동일하게 하되 api-id 변경.
                // 만약 경로가 다르다면 수정 필요. (질문 내용: /api/dostk/mrkcond)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("api-id", "ka10004") // 호가 잔량 조회 TR ID
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    logger.info("OrderBook API Response for {}: {}", stockCode, response);
                    return parseOrderBookResponse(stockCode, (Map<String, Object>) response);
                })
                .onErrorResume(e -> {
                    logger.error("OrderBook API call failed for {}: {}", stockCode, e.getMessage());
                    return Mono.empty();
                });
    }

    @SuppressWarnings("unchecked")
    private List<StockPriceInfo> parseResponse(String stockCode, Map<String, Object> response) {
        try {
            List<Map<String, String>> chartData = (List<Map<String, String>>) response.get("cntr_str_tm");

            if (chartData == null || chartData.isEmpty()) {
                logger.warn("No chart data found for {}: {}", stockCode, response);
                return List.of();
            }

            if (!chartData.isEmpty()) {
                return List.of(mapToStockPriceInfo(stockCode, chartData.get(0)));
            }
            return List.of();

        } catch (Exception e) {
            logger.error("Error parsing response for {}: {}", stockCode, e.getMessage());
            return List.of();
        }
    }

    private StockOrderBook parseOrderBookResponse(String stockCode, Map<String, Object> response) {
        try {
            // 응답 구조: 최상위 레벨에 필드 존재 (output 래퍼 없음)
            Map<String, Object> data = response;
            if (response.containsKey("output")) {
                data = (Map<String, Object>) response.get("output");
            }

            // 시간 파싱
            LocalDateTime time = LocalDateTime.now();
            String timeStr = getString(data, "bid_req_base_tm");
            if (timeStr != null && timeStr.length() == 6) {
                try {
                    LocalTime localTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HHmmss"));
                    time = LocalDateTime.of(LocalDate.now(), localTime);
                } catch (Exception e) {
                    logger.warn("Failed to parse order book time: {}", timeStr);
                }
            }

            return StockOrderBook.builder()
                    .stockCode(stockCode)
                    .time(time)
                    .totalSellRemain(parseLong(data, "tot_sel_req"))
                    .totalBuyRemain(parseLong(data, "tot_buy_req"))
                    .build();

        } catch (Exception e) {
            logger.error("Error parsing order book response for {}: {}", stockCode, e.getMessage());
            return null;
        }
    }

    private StockPriceInfo mapToStockPriceInfo(String stockCode, Map<String, String> data) {
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

    private String getString(Map<String, Object> data, String key) {
        return data.getOrDefault(key, "").toString();
    }

    private long parseLong(Object value) {
        if (value == null) return 0;
        String strVal = value.toString();
        if (strVal.trim().isEmpty()) return 0;
        try {
            return Long.parseLong(strVal.replace("+", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private long parseLong(Map<String, Object> data, String key) {
        return parseLong(data.get(key));
    }

    private double parseDouble(Object value) {
        if (value == null) return 0.0;
        String strVal = value.toString();
        if (strVal.trim().isEmpty()) return 0.0;
        try {
            return Double.parseDouble(strVal.replace("+", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void saveData(List<StockPriceInfo> infoList) {
        stockPriceInfoRepository.saveAll(infoList);
    }

    private void saveOrderBook(StockOrderBook orderBook) {
        stockOrderBookRepository.save(orderBook);
    }
}
