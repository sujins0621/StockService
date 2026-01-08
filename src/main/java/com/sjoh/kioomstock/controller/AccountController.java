package com.sjoh.kioomstock.controller;

import com.sjoh.kioomstock.domain.AccountInfo;
import com.sjoh.kioomstock.service.AccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/account")
    public Mono<AccountInfo> getAccountInfo() {
        return accountService.fetchAndSaveAccountInfo();
    }
}
