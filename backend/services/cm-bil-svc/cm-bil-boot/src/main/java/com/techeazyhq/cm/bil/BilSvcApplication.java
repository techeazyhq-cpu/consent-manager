package com.techeazyhq.cm.bil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.techeazyhq.cm.bil")
public class BilSvcApplication {

    public static void main(String[] args) {
        SpringApplication.run(BilSvcApplication.class, args);
    }
}
