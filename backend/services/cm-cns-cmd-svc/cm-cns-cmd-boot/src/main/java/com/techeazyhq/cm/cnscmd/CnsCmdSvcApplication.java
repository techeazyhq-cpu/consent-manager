package com.techeazyhq.cm.cnscmd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Composition root (plan §5) for the Consent Artefact Lifecycle (write side) service. */
@SpringBootApplication(scanBasePackages = "com.techeazyhq.cm.cnscmd")
@EnableScheduling
public class CnsCmdSvcApplication {

    public static void main(String[] args) {
        SpringApplication.run(CnsCmdSvcApplication.class, args);
    }
}
