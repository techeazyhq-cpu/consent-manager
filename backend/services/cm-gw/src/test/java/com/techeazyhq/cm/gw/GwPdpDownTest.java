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

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/** The PEP fails closed (ADR-006, ADR-016): with the PDP unreachable, a consent token is never honoured. */
@SpringBootTest(properties = "cm.pdp.base-url=http://127.0.0.1:1")
@AutoConfigureWebTestClient
class GwPdpDownTest {

    @Autowired
    private WebTestClient client;

    @Test
    void aVerificationIsDeniedWhenThePdpCannotBeReached() {
        client.mutateWith(mockJwt()).get()
                .uri("/api/cns-qry/cns-arts/art-1/verify")
                .header("X-Cns-Tkn", "token")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.type").isEqualTo("urn:cm:problem:consent-not-verified");
    }
}
