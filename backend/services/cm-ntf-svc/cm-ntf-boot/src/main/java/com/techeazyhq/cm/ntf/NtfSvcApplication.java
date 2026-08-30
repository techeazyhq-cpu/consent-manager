package com.techeazyhq.cm.ntf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.techeazyhq.cm.ntf")
public class NtfSvcApplication {

    public static void main(String[] args) {
        SpringApplication.run(NtfSvcApplication.class, args);
    }
}
