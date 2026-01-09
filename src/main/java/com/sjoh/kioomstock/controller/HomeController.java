package com.sjoh.kioomstock.controller;

import com.sjoh.kioomstock.domain.AccountInfo;
import com.sjoh.kioomstock.domain.AccountStockInfo;
import com.sjoh.kioomstock.domain.StockInvestor;
import com.sjoh.kioomstock.domain.StockOrderBook;
import com.sjoh.kioomstock.domain.StockPriceInfo;
import com.sjoh.kioomstock.repository.AccountInfoRepository;
import com.sjoh.kioomstock.repository.StockInvestorRepository;
import com.sjoh.kioomstock.repository.StockOrderBookRepository;
import com.sjoh.kioomstock.repository.StockPriceInfoRepository;
import com.sjoh.kioomstock.service.KiwoomAuthService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final KiwoomAuthService authService;
    private final StockPriceInfoRepository stockPriceInfoRepository;
    private final AccountInfoRepository accountInfoRepository;
    private final StockOrderBookRepository stockOrderBookRepository;
    private final StockInvestorRepository stockInvestorRepository;

    public HomeController(KiwoomAuthService authService, StockPriceInfoRepository stockPriceInfoRepository, AccountInfoRepository accountInfoRepository, StockOrderBookRepository stockOrderBookRepository, StockInvestorRepository stockInvestorRepository) {
        this.authService = authService;
        this.stockPriceInfoRepository = stockPriceInfoRepository;
        this.accountInfoRepository = accountInfoRepository;
        this.stockOrderBookRepository = stockOrderBookRepository;
        this.stockInvestorRepository = stockInvestorRepository;
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) String stockCode, Model model) {
        // 주식 데이터 조회 (시간 오름차순으로 정렬하여 그래프 그리기에 용이하게 함)
        List<StockPriceInfo> dataList;
        if (stockCode != null && !stockCode.isEmpty()) {
            dataList = stockPriceInfoRepository.findByStockCode(stockCode, Sort.by(Sort.Direction.ASC, "time"));
        } else {
            dataList = stockPriceInfoRepository.findAll(Sort.by(Sort.Direction.ASC, "time"));
        }
        
        // 호가 잔량 데이터 조회
        List<StockOrderBook> orderBookList = stockOrderBookRepository.findAll();
        
        // 투자자별 매매 현황 데이터 조회
        List<StockInvestor> investorList = stockInvestorRepository.findAll();

        // 데이터를 검색하기 쉽게 Map으로 변환
        DateTimeFormatter keyFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        Map<String, StockOrderBook> orderBookMap = new HashMap<>();
        for (StockOrderBook book : orderBookList) {
            String key = book.getStockCode() + "_" + book.getTime().format(keyFormatter);
            orderBookMap.put(key, book);
        }
        
        Map<String, StockInvestor> investorMap = new HashMap<>();
        for (StockInvestor investor : investorList) {
            String key = investor.getStockCode() + "_" + investor.getTime().format(keyFormatter);
            investorMap.put(key, investor);
        }

        // 그래프용 데이터 준비
        List<String> labels = new ArrayList<>();
        List<Long> prices = new ArrayList<>();
        List<Double> powers = new ArrayList<>();
        List<Long> volumes = new ArrayList<>();
        List<Long> sellRemains = new ArrayList<>();
        List<Long> buyRemains = new ArrayList<>();

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        for (StockPriceInfo info : dataList) {
            labels.add(info.getTime().format(timeFormatter));
            // 현재가 절대값 처리
            prices.add(Math.abs(info.getCurrentPrice()));
            powers.add(info.getVolumePower());
            volumes.add(info.getVolume());

            String key = info.getStockCode() + "_" + info.getTime().format(keyFormatter);
            StockOrderBook matchedBook = orderBookMap.get(key);
            if (matchedBook != null) {
                sellRemains.add(matchedBook.getTotalSellRemain());
                buyRemains.add(matchedBook.getTotalBuyRemain());
            } else {
                sellRemains.add(0L);
                buyRemains.add(0L);
            }
        }

        // 계좌 정보 조회
        List<AccountInfo> accountList = accountInfoRepository.findAll();
        AccountInfo accountInfo = accountList.isEmpty() ? null : accountList.get(0);

        // 필터링을 위한 종목 코드 목록 (중복 제거)
        List<String> stockCodes = stockPriceInfoRepository.findAll().stream()
                .map(StockPriceInfo::getStockCode)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        model.addAttribute("accountInfo", accountInfo);
        model.addAttribute("stockCodes", stockCodes);
        model.addAttribute("selectedStockCode", stockCode);
        
        // 그래프 데이터 전달
        model.addAttribute("labels", labels);
        model.addAttribute("prices", prices);
        model.addAttribute("powers", powers);
        model.addAttribute("volumes", volumes);
        model.addAttribute("sellRemains", sellRemains);
        model.addAttribute("buyRemains", buyRemains);
        
        // 테이블용 데이터 (역순 정렬)
        List<StockPriceInfo> tableDataList = new ArrayList<>(dataList);
        java.util.Collections.reverse(tableDataList);
        model.addAttribute("dataList", tableDataList);
        model.addAttribute("orderBookMap", orderBookMap);
        model.addAttribute("investorMap", investorMap);
        model.addAttribute("keyFormatter", keyFormatter);
        
        // 스타일 계산을 위한 헬퍼 객체 추가
        model.addAttribute("styleHelper", new StyleHelper());

        return "home";
    }
    
    // Thymeleaf에서 복잡한 로직을 처리하기 위한 헬퍼 클래스
    public static class StyleHelper {
        public String getSellRemainStyle(long sell, long buy) {
            if (sell > buy) {
                long total = sell + buy;
                double ratio = total == 0 ? 0 : (double) sell / total;
                double alpha = 0.2 + (ratio - 0.5) * 1.6;
                return "background-color: rgba(255, 192, 203, " + String.format("%.2f", alpha) + ")";
            }
            return "";
        }
        
        public String getBuyRemainStyle(long sell, long buy) {
            if (buy > sell) {
                long total = sell + buy;
                double ratio = total == 0 ? 0 : (double) buy / total;
                double alpha = 0.2 + (ratio - 0.5) * 1.6;
                return "background-color: rgba(255, 192, 203, " + String.format("%.2f", alpha) + ")";
            }
            return "";
        }

        public String getVolumePowerStyle(double power) {
            if (power > 100) {
                double diff = Math.min(power - 100, 100);
                double alpha = 0.2 + (diff / 100.0) * 0.8;
                return "background-color: rgba(255, 99, 71, " + String.format("%.2f", alpha) + ")";
            } else if (power < 100) {
                double diff = Math.min(100 - power, 100);
                double alpha = 0.2 + (diff / 100.0) * 0.8;
                return "background-color: rgba(135, 206, 235, " + String.format("%.2f", alpha) + ")";
            }
            return "";
        }

        public String getInvestorStyle(long value) {
            if (value > 0) {
                // 순매수 양수: 빨간색
                // 최대 10억(1,000,000,000) 기준으로 농도 조절 (임의 설정)
                // 또는 로그 스케일 적용 가능. 여기선 간단히 10000주 기준으로 해봄 (단위가 주식 수라면)
                // 예제 데이터가 1000~60000 수준이므로 50000을 Max로 잡음
                double max = 50000.0;
                double ratio = Math.min(value, max) / max;
                double alpha = 0.2 + ratio * 0.8;
                return "background-color: rgba(255, 99, 71, " + String.format("%.2f", alpha) + ")";
            } else if (value < 0) {
                // 순매수 음수: 파란색
                double max = 50000.0;
                double ratio = Math.min(Math.abs(value), max) / max;
                double alpha = 0.2 + ratio * 0.8;
                return "background-color: rgba(135, 206, 235, " + String.format("%.2f", alpha) + ")";
            }
            return "";
        }
    }

    @GetMapping("/login")
    @ResponseBody
    public Mono<String> login() {
        return authService.refreshAccessToken()
                .map(token -> "<html><head><meta charset=\"UTF-8\"></head><body><h1>로그인 성공</h1><p>토큰이 발급되었습니다.</p><a href='/'>홈으로 이동</a></body></html>")
                .onErrorResume(e -> Mono.just("<html><head><meta charset=\"UTF-8\"></head><body><h1>로그인 실패</h1><p>" + e.getMessage() + "</p><a href='/'>홈으로 이동</a></body></html>"));
    }
}
