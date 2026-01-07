package com.sjoh.kioomstock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class KioomStockApplication {

    public static void main(String[] args) {
        SpringApplication.run(KioomStockApplication.class, args);
    }

}
