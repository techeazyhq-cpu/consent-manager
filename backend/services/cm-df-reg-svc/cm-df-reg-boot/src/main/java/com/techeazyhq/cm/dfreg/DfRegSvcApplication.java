package com.techeazyhq.cm.dfreg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.techeazyhq.cm.dfreg")
@EnableScheduling
public class DfRegSvcApplication {

    public static void main(String[] args) {
        SpringApplication.run(DfRegSvcApplication.class, args);
    }
}
