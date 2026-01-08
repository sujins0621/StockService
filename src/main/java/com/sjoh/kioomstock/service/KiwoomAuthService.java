package com.sjoh.kioomstock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Service
public class KiwoomAuthService {

    private static final Logger logger = LoggerFactory.getLogger(KiwoomAuthService.class);

    private final WebClient webClient;

    @Value("${kiwoom.api.key:}")
    private String apiKey;

    @Value("${kiwoom.api.secret:}")
    private String apiSecret;

    // 발급받은 토큰을 메모리에 저장
    private String cachedToken;

    public KiwoomAuthService(WebClient webClient) {
        this.webClient = webClient;
    }

    @PostConstruct
    public void init() {
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            logger.warn("API Key or Secret is not configured. Skipping token initialization.");
            return;
        }
        refreshAccessToken().subscribe(
                token -> logger.info("Successfully initialized OAuth token."),
                error -> logger.error("Failed to initialize OAuth token", error)
        );
    }

    /**
     * 유효한 토큰을 반환합니다.
     * 토큰이 없으면 새로 발급받습니다. (만료 처리는 별도 로직 필요)
     */
    public Mono<String> getAccessToken() {
        if (cachedToken != null && !cachedToken.isEmpty()) {
            return Mono.just(cachedToken);
        }
        return refreshAccessToken();
    }
//application/json;charset=UTF-8
    public Mono<String> refreshAccessToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("appkey", apiKey);
        formData.add("appsecret", apiSecret);

        return webClient.post()
                .uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    String token = (String) response.get("access_token");
                    this.cachedToken = token;
                    return token;
                });
    }
}
