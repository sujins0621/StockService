package com.sjoh.kioomstock.controller;

import com.sjoh.kioomstock.domain.AccountInfo;
import com.sjoh.kioomstock.domain.AccountStockInfo;
import com.sjoh.kioomstock.domain.StockOrderBook;
import com.sjoh.kioomstock.domain.StockPriceInfo;
import com.sjoh.kioomstock.repository.AccountInfoRepository;
import com.sjoh.kioomstock.repository.StockOrderBookRepository;
import com.sjoh.kioomstock.repository.StockPriceInfoRepository;
import com.sjoh.kioomstock.service.KiwoomAuthService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final KiwoomAuthService authService;
    private final StockPriceInfoRepository stockPriceInfoRepository;
    private final AccountInfoRepository accountInfoRepository;
    private final StockOrderBookRepository stockOrderBookRepository;

    public HomeController(KiwoomAuthService authService, StockPriceInfoRepository stockPriceInfoRepository, AccountInfoRepository accountInfoRepository, StockOrderBookRepository stockOrderBookRepository) {
        this.authService = authService;
        this.stockPriceInfoRepository = stockPriceInfoRepository;
        this.accountInfoRepository = accountInfoRepository;
        this.stockOrderBookRepository = stockOrderBookRepository;
    }

    @GetMapping("/")
    public String home(Model model) {
        // 주식 데이터 조회 (시간 오름차순으로 정렬하여 그래프 그리기에 용이하게 함)
        List<StockPriceInfo> dataList = stockPriceInfoRepository.findAll(Sort.by(Sort.Direction.ASC, "time"));
        
        // 호가 잔량 데이터 조회
        List<StockOrderBook> orderBookList = stockOrderBookRepository.findAll();
        
        // 호가 잔량 데이터를 검색하기 쉽게 Map으로 변환 (Key: 종목코드_yyyyMMddHHmm)
        Map<String, StockOrderBook> orderBookMap = new HashMap<>();
        DateTimeFormatter keyFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        
        for (StockOrderBook book : orderBookList) {
            String key = book.getStockCode() + "_" + book.getTime().format(keyFormatter);
            orderBookMap.put(key, book);
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
            prices.add(info.getCurrentPrice());
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

        model.addAttribute("accountInfo", accountInfo);
        
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
        model.addAttribute("keyFormatter", keyFormatter);

        return "home";
    }

    @GetMapping("/login")
    @ResponseBody
    public Mono<String> login() {
        return authService.refreshAccessToken()
                .map(token -> "<html><head><meta charset=\"UTF-8\"></head><body><h1>로그인 성공</h1><p>토큰이 발급되었습니다.</p><a href='/'>홈으로 이동</a></body></html>")
                .onErrorResume(e -> Mono.just("<html><head><meta charset=\"UTF-8\"></head><body><h1>로그인 실패</h1><p>" + e.getMessage() + "</p><a href='/'>홈으로 이동</a></body></html>"));
    }
}
