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

package com.techeazyhq.cm.cnscmd.app;

import java.time.Clock;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.cnscmd.dom.PrpsDefRepoPort;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;
import com.techeazyhq.cm.cnscmd.dom.Processor;
import com.techeazyhq.cm.cnscmd.dom.ProcessorRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A fiduciary's register of processors (ADR-075): add one, describe it, retire it, and see which purposes name it. Each change
 * is recorded with who made it (ADR-040), in the same transaction.
 */
public class ProcessorCmds {

    private final ProcessorRepoPort repo;
    private final PrpsDefRepoPort purposes;
    private final AuditTrail audit;
    private final Clock clock;

    public ProcessorCmds(ProcessorRepoPort repo, PrpsDefRepoPort purposes, AuditTrail audit, Clock clock) {
        this.repo = repo;
        this.purposes = purposes;
        this.audit = audit;
        this.clock = clock;
    }

    public Flux<Processor> list(String dfId) {
        return repo.list(dfId);
    }

    public Mono<Processor> get(String dfId, String id) {
        return found(dfId, id);
    }

    @Transactional
    public Mono<Processor> register(String dfId, String name, String role, String country, String contactEmail) {
        return invalidAs400(() -> Processor.register(dfId, name, role, country, contactEmail, clock.instant()))
                .flatMap(p -> repo.findByName(dfId, p.name())
                        .flatMap(existing -> Mono.<Processor>error(new DomRuleViolation(409, "processor-exists",
                                "There is already a processor called " + existing.name())))
                        .switchIfEmpty(Mono.defer(() -> repo.save(p)
                                .flatMap(saved -> audit.record("processor.registered", dfId, saved.id(),
                                        AuditTrail.details("processor", saved.name())).thenReturn(saved)))));
    }

    @Transactional
    public Mono<Processor> describe(String dfId, String id, String role, String country, String contactEmail) {
        return found(dfId, id)
                .flatMap(p -> invalidAs400(() -> p.describe(role, country, contactEmail)))
                .flatMap(repo::save)
                .flatMap(saved -> audit.record("processor.updated", dfId, saved.id(),
                        AuditTrail.details("processor", saved.name())).thenReturn(saved));
    }

    /** No new draft or publication may name it from now on; versions that already did keep reading true. */
    @Transactional
    public Mono<Processor> retire(String dfId, String id) {
        return found(dfId, id)
                .flatMap(p -> repo.save(p.retire(clock.instant())))
                .flatMap(saved -> audit.record("processor.retired", dfId, saved.id(),
                        AuditTrail.details("processor", saved.name())).thenReturn(saved));
    }

    @Transactional
    public Mono<Processor> reinstate(String dfId, String id) {
        return found(dfId, id)
                .flatMap(p -> repo.save(p.reinstate()))
                .flatMap(saved -> audit.record("processor.reinstated", dfId, saved.id(),
                        AuditTrail.details("processor", saved.name())).thenReturn(saved));
    }

    /** The purposes whose current version, or draft, names the processor: what to change before retiring it. */
    public Mono<List<Use>> uses(String dfId, String id) {
        return found(dfId, id).flatMap(p -> purposes.listByDf(dfId)
                .flatMap(def -> Flux.fromStream(java.util.stream.Stream.concat(def.current().stream(), def.draft().stream()))
                        .filter(v -> names(v.content(), p.name()))
                        .map(v -> new Use(def.code(), v.verNo(), v.sts().name())))
                .collectList());
    }

    /** Where a processor is named. */
    public record Use(String purpose, int version, String status) { }

    /**
     * Refuses content that names a retired processor (ADR-075). Names that are not in the register at all are accepted: the
     * register is opt-in, so a fiduciary that has not started one keeps writing recipients as it always has.
     */
    public static Mono<Void> refuseRetired(ProcessorRepoPort repo, String dfId, PrpsVerContent content) {
        return repo.list(dfId).filter(p -> !p.active())
                .filter(p -> names(content, p.name()))
                .next()
                .flatMap(p -> Mono.<Void>error(new DomRuleViolation(409, "processor-retired",
                        p.name() + " is retired: remove it from the recipients and transfers, or reinstate it")));
    }

    static boolean names(PrpsVerContent content, String processorName) {
        String key = Processor.key(processorName);
        return java.util.stream.Stream.concat(content.recipients().stream(), content.transfers().stream())
                .anyMatch(s -> Processor.key(s).equals(key));
    }

    private Mono<Processor> found(String dfId, String id) {
        return repo.find(dfId, id).switchIfEmpty(Mono.error(() -> new DomRuleViolation(404, "processor-not-found",
                "There is no such processor in your register")));
    }

    private static <T> Mono<T> invalidAs400(java.util.function.Supplier<T> action) {
        try {
            return Mono.just(action.get());
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-processor", e.getMessage()));
        }
    }
}
