package com.sjoh.kioomstock.controller;

import com.sjoh.kioomstock.service.KiwoomAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HomeController {

    private final KiwoomAuthService authService;

    public HomeController(KiwoomAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/")
    public String home() {
        return "<html>" +
                "<body>" +
                "<h1>Kiwoom Stock Service</h1>" +
                "<button onclick=\"location.href='/login'\">Login to Kiwoom</button>" +
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
