package com.sjoh.kioomstock.service;

import com.sjoh.kioomstock.domain.StockDailyCandle;
import com.sjoh.kioomstock.domain.StockInvestor;
import com.sjoh.kioomstock.domain.StockOrderBook;
import com.sjoh.kioomstock.domain.StockPriceInfo;
import com.sjoh.kioomstock.repository.StockDailyCandleRepository;
import com.sjoh.kioomstock.repository.StockInvestorRepository;
import com.sjoh.kioomstock.repository.StockOrderBookRepository;
import com.sjoh.kioomstock.repository.StockPriceInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
import java.util.stream.Collectors;

@Service
public class StockDataService {

    private static final Logger logger = LoggerFactory.getLogger(StockDataService.class);

    private final WebClient webClient;
    private final KiwoomAuthService authService;
    private final StockPriceInfoRepository stockPriceInfoRepository;
    private final StockOrderBookRepository stockOrderBookRepository;
    private final StockDailyCandleRepository stockDailyCandleRepository;
    private final StockInvestorRepository stockInvestorRepository;

    // 모니터링할 종목 리스트 (예: 삼성전자 005930)
    private final List<String> targetStockCodes = List.of("005930","000660","122630","114800");

    public StockDataService(WebClient webClient, KiwoomAuthService authService, StockPriceInfoRepository stockPriceInfoRepository, StockOrderBookRepository stockOrderBookRepository, StockDailyCandleRepository stockDailyCandleRepository, StockInvestorRepository stockInvestorRepository) {
        this.webClient = webClient;
        this.authService = authService;
        this.stockPriceInfoRepository = stockPriceInfoRepository;
        this.stockOrderBookRepository = stockOrderBookRepository;
        this.stockDailyCandleRepository = stockDailyCandleRepository;
        this.stockInvestorRepository = stockInvestorRepository;
    }

    // 서버 시작 시 1회 실행 (테스트용)
    @EventListener(ApplicationReadyEvent.class)
    public void initDataCollection() {
        logger.info("Executing initial data collection on startup...");
        collectStockData();
    }

    // 평일 09:00 ~ 15:30 사이에 1분마다 실행 (장 운영 시간)
    @Scheduled(cron = "0 * 9-15 * * MON-FRI")
    public void collectStockData() {
        // 현재 시간이 15:30 이후인지 체크 (15시 대에는 0~59분 모두 실행되므로)
        LocalTime now = LocalTime.now();
        if (now.getHour() == 15 && now.getMinute() > 30) {
            return;
        }

        logger.info("Starting scheduled stock data collection...");

        authService.getAccessToken()
                .flatMapMany(token -> Flux.fromIterable(targetStockCodes)
                        .flatMap(code -> Mono.zip(
                                fetchStockPrice(token, code),
                                fetchOrderBook(token, code),
                                fetchDailyCandle(token, code),
                                fetchInvestorInfo(token, code)
                        ).doOnNext(tuple -> {
                            List<StockPriceInfo> priceInfos = tuple.getT1();
                            StockOrderBook orderBook = tuple.getT2();
                            List<StockDailyCandle> dailyCandles = tuple.getT3();
                            List<StockInvestor> investors = tuple.getT4();

                            if (priceInfos != null && !priceInfos.isEmpty()) {
                                logger.info("Collected {} data points for {}", priceInfos.size(), code);
                                saveData(priceInfos);
                            }
                            if (orderBook != null) {
                                logger.info("Collected order book for {}: {}", code, orderBook);
                                saveOrderBook(orderBook);
                            }
                            if (dailyCandles != null && !dailyCandles.isEmpty()) {
                                logger.info("Collected {} daily candles for {}", dailyCandles.size(), code);
                                saveDailyCandles(dailyCandles);
                            }
                            if (investors != null && !investors.isEmpty()) {
                                logger.info("Collected {} investor records for {}", investors.size(), code);
                                saveInvestors(investors);
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
                .uri("/api/dostk/mrkcond")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("api-id", "ka10004")
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

    private Mono<List<StockDailyCandle>> fetchDailyCandle(String token, String stockCode) {
        logger.info("fetchDailyCandle CALL for {}", stockCode);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("stk_cd", stockCode);
        requestBody.put("base_dt", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))); // 오늘 날짜 기준
        requestBody.put("upd_stkpc_tp", "0"); // 수정주가구분 (0: 미적용, 1: 적용) - 가이드에 따라 선택

        return webClient.post()
                .uri("/api/dostk/chart") // 주식 일봉 차트 조회
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("api-id", "ka10081") // TR ID
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    logger.info("DailyCandle API Response for {}: {}", stockCode, response);
                    return parseDailyCandleResponse(stockCode, (Map<String, Object>) response);
                })
                .onErrorResume(e -> {
                    logger.error("DailyCandle API call failed for {}: {}", stockCode, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private Mono<List<StockInvestor>> fetchInvestorInfo(String token, String stockCode) {
        logger.info("fetchInvestorInfo CALL for {}", stockCode);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("dt", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        requestBody.put("stk_cd", stockCode);
        requestBody.put("amt_qty_tp", "2"); // 1:금액, 2:수량 (예제는 수량 기준인듯)
        requestBody.put("trde_tp", "0"); // 0:순매수
        requestBody.put("unit_tp", "1"); // 1:단주

        return webClient.post()
                .uri("/api/dostk/stkinfo")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("api-id", "ka10059")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    logger.info("Investor API Response for {}: {}", stockCode, response);
                    return parseInvestorResponse(stockCode, (Map<String, Object>) response);
                })
                .onErrorResume(e -> {
                    logger.error("Investor API call failed for {}: {}", stockCode, e.getMessage());
                    return Mono.just(List.of());
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
            Map<String, Object> data = response;
            if (response.containsKey("output")) {
                data = (Map<String, Object>) response.get("output");
            }

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

    @SuppressWarnings("unchecked")
    private List<StockDailyCandle> parseDailyCandleResponse(String stockCode, Map<String, Object> response) {
        try {
            List<Map<String, String>> chartData = (List<Map<String, String>>) response.get("stk_dt_pole_chart_qry");

            if (chartData == null || chartData.isEmpty()) {
                logger.warn("No daily candle data found for {}: {}", stockCode, response);
                return List.of();
            }

            // 최근 일주일(7일) 데이터만 필터링
            LocalDate oneWeekAgo = LocalDate.now().minusDays(7);

            return chartData.stream()
                    .map(data -> mapToStockDailyCandle(stockCode, data))
                    .filter(candle -> !candle.getDate().isBefore(oneWeekAgo)) // 7일 이전 데이터 제외
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error parsing daily candle response for {}: {}", stockCode, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<StockInvestor> parseInvestorResponse(String stockCode, Map<String, Object> response) {
        try {
            List<Map<String, String>> investorData = (List<Map<String, String>>) response.get("stk_invsr_orgn");

            if (investorData == null || investorData.isEmpty()) {
                logger.warn("No investor data found for {}: {}", stockCode, response);
                return List.of();
            }

            // 오늘 날짜 데이터만 필터링
            LocalDate today = LocalDate.now();

            return investorData.stream()
                    .map(data -> mapToStockInvestor(stockCode, data))
                    .filter(investor -> investor.getDate().isEqual(today)) // 오늘 날짜만 포함
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error parsing investor response for {}: {}", stockCode, e.getMessage());
            return List.of();
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

    private StockDailyCandle mapToStockDailyCandle(String stockCode, Map<String, String> data) {
        LocalDate date = LocalDate.now();
        String dateStr = data.get("dt");
        if (dateStr != null && dateStr.length() == 8) {
            try {
                date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
            } catch (Exception e) {
                logger.warn("Failed to parse date: {}", dateStr);
            }
        }

        return StockDailyCandle.builder()
                .stockCode(stockCode)
                .date(date)
                .closePrice(parseLong(data.get("cur_prc")))
                .volume(parseLong(data.get("trde_qty")))
                .tradingValue(parseLong(data.get("trde_prica")))
                .openPrice(parseLong(data.get("open_pric")))
                .highPrice(parseLong(data.get("high_pric")))
                .lowPrice(parseLong(data.get("low_pric")))
                .changeFromPrev(parseLong(data.get("pred_pre")))
                .changeSign(data.getOrDefault("pred_pre_sig", ""))
                .turnoverRate(parseDouble(data.get("trde_tern_rt")))
                .build();
    }

    private StockInvestor mapToStockInvestor(String stockCode, Map<String, String> data) {
        LocalDate date = LocalDate.now();
        String dateStr = data.get("dt");
        if (dateStr != null && dateStr.length() == 8) {
            try {
                date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
            } catch (Exception e) {
                logger.warn("Failed to parse date: {}", dateStr);
            }
        }

        return StockInvestor.builder()
                .time(LocalDateTime.now()) // 수집 시간 저장 (StockPriceInfo와 동일한 타입)
                .stockCode(stockCode)
                .date(date)
                .currentPrice(parseLong(data.get("cur_prc")))
                .changeFromPrev(parseLong(data.get("pred_pre")))
                .fluctuationRate(parseDouble(data.get("flu_rt")))
                .volume(parseLong(data.get("acc_trde_qty")))
                .tradingValue(parseLong(data.get("acc_trde_prica")))
                .individual(parseLong(data.get("ind_invsr")))
                .foreigner(parseLong(data.get("frgnr_invsr")))
                .institution(parseLong(data.get("orgn")))
                .financialInvestment(parseLong(data.get("fnnc_invt")))
                .insurance(parseLong(data.get("insrnc")))
                .investmentTrust(parseLong(data.get("invtrt")))
                .etcFinance(parseLong(data.get("etc_fnnc")))
                .bank(parseLong(data.get("bank")))
                .pensionFund(parseLong(data.get("penfnd_etc")))
                .privateFund(parseLong(data.get("samo_fund")))
                .nation(parseLong(data.get("natn")))
                .etcCorp(parseLong(data.get("etc_corp")))
                .foreignNational(parseLong(data.get("natfor")))
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
        for (StockPriceInfo info : infoList) {
            // 중복 체크: 이미 존재하는 데이터면 저장하지 않음 (또는 업데이트)
            if (stockPriceInfoRepository.findByStockCodeAndTime(info.getStockCode(), info.getTime()).isEmpty()) {
                stockPriceInfoRepository.save(info);
            } else {
                logger.debug("Skipping duplicate StockPriceInfo for {} at {}", info.getStockCode(), info.getTime());
            }
        }
    }

    private void saveOrderBook(StockOrderBook orderBook) {
        stockOrderBookRepository.save(orderBook);
    }

    private void saveDailyCandles(List<StockDailyCandle> candles) {
        for (StockDailyCandle candle : candles) {
            // 중복 체크: 이미 존재하는 데이터면 저장하지 않음 (또는 업데이트)
            // 여기서는 이미 존재하면 업데이트하지 않고 스킵하거나, 필요 시 업데이트 로직 추가
            // findByStockCodeAndDate는 이미 Repository에 추가됨
            if (stockDailyCandleRepository.findByStockCodeAndDate(candle.getStockCode(), candle.getDate()).isEmpty()) {
                stockDailyCandleRepository.save(candle);
            } else {
                logger.debug("Skipping duplicate StockDailyCandle for {} at {}", candle.getStockCode(), candle.getDate());
            }
        }
    }

    private void saveInvestors(List<StockInvestor> investors) {
        // 투자자 정보는 timestamp가 키이므로 중복 체크 없이 저장 (또는 필요 시 로직 추가)
        // 여기서는 매번 수집 시마다 새로운 timestamp로 저장됨
        stockInvestorRepository.saveAll(investors);
    }
}
