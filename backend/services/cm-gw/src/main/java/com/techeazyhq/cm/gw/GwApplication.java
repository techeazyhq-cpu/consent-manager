package com.techeazyhq.cm.gw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root for the API Gateway (plan §9/§10) — single entry point for all 3
 * frontends and the DF Integration API, HTTP/2, PEP for consent-artefact verification,
 * and per-DF rate limiting sourced from cm-bil-svc's subscription quotas.
 */
@SpringBootApplication
public class GwApplication {

    public static void main(String[] args) {
        SpringApplication.run(GwApplication.class, args);
    }
}
