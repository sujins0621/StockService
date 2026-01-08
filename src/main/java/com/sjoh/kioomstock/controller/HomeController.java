package com.sjoh.kioomstock.controller;

import com.sjoh.kioomstock.domain.StockPriceInfo;
import com.sjoh.kioomstock.repository.StockPriceInfoRepository;
import com.sjoh.kioomstock.service.KiwoomAuthService;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
public class HomeController {

    private final KiwoomAuthService authService;
    private final StockPriceInfoRepository stockPriceInfoRepository;

    public HomeController(KiwoomAuthService authService, StockPriceInfoRepository stockPriceInfoRepository) {
        this.authService = authService;
        this.stockPriceInfoRepository = stockPriceInfoRepository;
    }

    @GetMapping("/")
    public String home() {
        List<StockPriceInfo> dataList = stockPriceInfoRepository.findAll(Sort.by(Sort.Direction.DESC, "time"));

        StringBuilder tableRows = new StringBuilder();
        for (StockPriceInfo info : dataList) {
            tableRows.append("<tr>")
                    .append("<td>").append(info.getTime()).append("</td>")
                    .append("<td>").append(info.getStockCode()).append("</td>")
                    .append("<td>").append(info.getCurrentPrice()).append("</td>")
                    .append("<td>").append(info.getDiffFromPrev()).append("</td>")
                    .append("<td>").append(info.getFluctuationRate()).append("</td>")
                    .append("<td>").append(info.getVolume()).append("</td>")
                    .append("<td>").append(info.getVolumePower()).append("</td>")
                    .append("<td>").append(info.getVolumePower5Min()).append("</td>")
                    .append("<td>").append(info.getVolumePower20Min()).append("</td>")
                    .append("<td>").append(info.getVolumePower60Min()).append("</td>")
                    .append("</tr>");
        }

        return "<html>" +
                "<head><style>table, th, td { border: 1px solid black; border-collapse: collapse; padding: 5px; }</style></head>" +
                "<body>" +
                "<h1> Stock Service</h1>" +
                "<button onclick=\"location.href='/login'\">Login to Kiwoom (Token Refresh)</button>" +
                "<br/><br/>" +
                "<h2>Collected Stock Data (Volume Power Trend)</h2>" +
                "<table>" +
                "<thead><tr><th>Time</th><th>Code</th><th>Price</th><th>Diff</th><th>Rate</th><th>Volume</th><th>Power</th><th>5m</th><th>20m</th><th>60m</th></tr></thead>" +
                "<tbody>" +
                tableRows.toString() +
                "</tbody>" +
                "</table>" +
                "</body>" +
                "</html>";
    }

    @GetMapping("/login")
    public Mono<String> login() {
        return authService.refreshAccessToken()
                .map(token -> "<html><body><h1>Login Successful</h1><p>Token acquired.</p><a href='/'>Go Home</a></body></html>")
                .onErrorResume(e -> Mono.just("<html><body><h1>Login Failed</h1><p>" + e.getMessage() + "</p><a href='/'>Go Home</a></body></html>"));
    }
}
