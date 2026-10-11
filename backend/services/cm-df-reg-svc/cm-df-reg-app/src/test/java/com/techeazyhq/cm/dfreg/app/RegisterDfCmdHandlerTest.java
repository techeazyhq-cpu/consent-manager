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

package com.techeazyhq.cm.dfreg.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RegisterDfCmdHandlerTest {

    private final Map<String, Df> saved = new LinkedHashMap<>();
    private final DfRepoPort repo = new DfRepoPort() {
        @Override
        public Mono<Df> save(Df df) {
            saved.put(df.id(), df);
            return Mono.just(df);
        }

        @Override
        public Mono<Df> findById(String id) {
            return Mono.justOrEmpty(saved.get(id));
        }

        @Override
        public Flux<Df> findAll() {
            return Flux.fromIterable(saved.values());
        }
    };
    private final RegisterDfCmdHandler handler =
            new RegisterDfCmdHandler(repo, name -> Mono.just(Set.of("FREE", "STANDARD").contains(name)));

    @Test
    void registersAFiduciaryWithItsProfileAndChosenPlan() {
        Df df = handler.handle(new RegisterDfCmd("Acme Health", "ops@acme.example", new RegisterDfCmd.PrflFields(
                "Acme Health Private Limited", null, null, null, "Healthcare", null, "29ABCDE1234F1Z5", "Asha Rao",
                "dpo@acme.example"), "STANDARD")).block();

        assertEquals("STANDARD", df.subPlnName());
        assertEquals("29ABCDE1234F1Z5", df.prfl().gstin());
        assertTrue(saved.containsKey(df.id()));
    }

    @Test
    void anInvalidProfileIsA400AndNothingIsSaved() {
        DomRuleViolation refused = assertThrows(DomRuleViolation.class, () -> handler.handle(new RegisterDfCmd(
                "Acme", "ops@acme.example", new RegisterDfCmd.PrflFields(null, "12345", null, null, null, null, null,
                null, null), null)).block());

        assertEquals(400, refused.httpStatus());
        assertEquals("invalid-df-profile", refused.problemType());
        assertEquals("contactPhone is not valid", refused.getMessage());
        assertTrue(saved.isEmpty());
    }

    @Test
    void aPlanBillingDoesNotOfferIsA400AndNothingIsSaved() {
        DomRuleViolation refused = assertThrows(DomRuleViolation.class, () -> handler.handle(
                new RegisterDfCmd("Acme", "ops@acme.example", null, "PLATINUM")).block());

        assertEquals("unknown-sub-pln", refused.problemType());
        assertTrue(saved.isEmpty());
    }

    @Test
    void withoutAPlanBillingIsNotAsked() {
        RegisterDfCmdHandler offline = new RegisterDfCmdHandler(repo,
                name -> Mono.error(new IllegalStateException("cm-bil-svc must not be called")));

        Df df = offline.handle(new RegisterDfCmd("Acme", "ops@acme.example", null, null)).block();

        assertEquals(null, df.subPlnName());
    }
}
