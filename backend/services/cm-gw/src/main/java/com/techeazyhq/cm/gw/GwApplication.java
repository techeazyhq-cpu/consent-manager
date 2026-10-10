/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

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
