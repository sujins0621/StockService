package com.sjoh.kioomstock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
public class KiwoomAuthService {

    private static final Logger logger = LoggerFactory.getLogger(KiwoomAuthService.class);

    private final WebClient webClient;

    @Value("${kiwoom.api.key:}")
    private String apiKey;

    @Value("${kiwoom.api.secret:}")
    private String apiSecret;

    @Value("${kiwoom.api.customer-id:}")
    private String customerId;

    public KiwoomAuthService(WebClient webClient) {
        this.webClient = webClient;
    }

    @PostConstruct
    public void init() {
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            logger.warn("Kiwoom API Key or Secret is not configured. Skipping token initialization.");
            return;
        }
        getAccessToken().subscribe(
                token -> logger.info("Successfully retrieved OAuth token: {}", token),
                error -> logger.error("Failed to retrieve OAuth token", error)
        );
    }

    public Mono<String> getAccessToken() {
        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", apiKey);
        body.put("appsecret", apiSecret);

        return webClient.post()
                .uri("/oauth2.0/token")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("access_token"));
    }
}
