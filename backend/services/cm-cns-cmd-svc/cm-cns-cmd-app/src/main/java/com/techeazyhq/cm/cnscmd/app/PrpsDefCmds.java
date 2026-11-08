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
import java.util.function.Function;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.cnscmd.dom.PrpsDef;
import com.techeazyhq.cm.cnscmd.dom.PrpsDefRepoPort;
import com.techeazyhq.cm.cnscmd.dom.PrpsParty;
import com.techeazyhq.cm.cnscmd.dom.PrpsVer;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;
import com.techeazyhq.cm.cnscmd.dom.ProcessorRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * What a fiduciary does with its purposes (ADR-031): define one, draft its next version, publish or discard the
 * draft. Each change is recorded with who made it (ADR-040), in the same transaction.
 */
public class PrpsDefCmds {

    private final PrpsDefRepoPort repo;
    private final ProcessorRepoPort processors;
    private final AuditTrail audit;
    private final Clock clock;

    public PrpsDefCmds(PrpsDefRepoPort repo, ProcessorRepoPort processors, AuditTrail audit, Clock clock) {
        this.repo = repo;
        this.processors = processors;
        this.audit = audit;
        this.clock = clock;
    }

    public Flux<PrpsDef> list(String dfId) {
        return repo.listByDf(dfId);
    }

    public Mono<PrpsDef> get(String dfId, String code) {
        return found(dfId, code);
    }

    /** A new purpose, its first version a draft. */
    @Transactional
    public Mono<PrpsDef> create(String dfId, String code, PrpsVerContent content) {
        return repo.findByCode(dfId, code)
                .flatMap(existing -> Mono.<PrpsDef>error(new DomRuleViolation(409, "purpose-exists",
                        "There is already a purpose " + code + ": draft a new version of it instead")))
                .switchIfEmpty(Mono.defer(() -> ProcessorCmds.refuseRetired(processors, dfId, content)
                        .then(invalidAs400(() -> PrpsDef.create(dfId, code, content, clock.instant())))
                        .flatMap(repo::save)
                        .flatMap(def -> audit.record("purpose.created", dfId, def.code(),
                                AuditTrail.details("purpose", def.code())).thenReturn(def))));
    }

    /** Starts or replaces the draft of the next version. */
    @Transactional
    public Mono<PrpsDef> saveDraft(String dfId, String code, PrpsVerContent content) {
        return ProcessorCmds.refuseRetired(processors, dfId, content).then(change(dfId, code, def -> {
            PrpsVer draft = def.saveDraft(content, clock.instant());
            return audit.record("purpose.draft-saved", dfId, def.code(),
                    AuditTrail.details("purpose", def.code(), "version", String.valueOf(draft.verNo())));
        }));
    }

    @Transactional
    public Mono<PrpsDef> discardDraft(String dfId, String code) {
        return change(dfId, code, def -> {
            int verNo = def.draft().map(PrpsVer::verNo).orElse(0);
            def.discardDraft();
            return audit.record("purpose.draft-discarded", dfId, def.code(),
                    AuditTrail.details("purpose", def.code(), "version", String.valueOf(verNo)));
        });
    }

    /** Publishes the draft as the current version; from now on requests are made from it. */
    @Transactional
    public Mono<PrpsDef> publish(String dfId, String code) {
        // a processor may have been retired since the draft was saved; the parties the version names are described as the register
        // describes them now, once, for good (ADR-081)
        return found(dfId, code)
                .flatMap(def -> def.draft()
                        .map(d -> ProcessorCmds.refuseRetired(processors, dfId, d.content()).then(partiesOf(dfId, d.content())))
                        .orElse(Mono.just(List.<PrpsParty>of())))
                .flatMap(parties -> change(dfId, code, def -> {
            def.draft().ifPresent(d -> def.saveDraft(d.content().withParties(parties), clock.instant()));
            PrpsVer published = def.publish(clock.instant());
            return audit.record("purpose.published", dfId, def.code(),
                    AuditTrail.details("purpose", def.code(), "version", String.valueOf(published.verNo()),
                            "change", published.change().kind().name()));
        }));
    }

    /** The register's details of each recipient and transfer the content names, in the order it names them; names not in the register have none. */
    private Mono<List<PrpsParty>> partiesOf(String dfId, PrpsVerContent content) {
        return processors.list(dfId).collectList().map(register -> {
            java.util.Map<String, com.techeazyhq.cm.cnscmd.dom.Processor> byKey = new java.util.HashMap<>();
            register.forEach(p -> byKey.put(com.techeazyhq.cm.cnscmd.dom.Processor.key(p.name()), p));
            java.util.LinkedHashMap<String, PrpsParty> out = new java.util.LinkedHashMap<>();
            java.util.stream.Stream.concat(content.recipients().stream(), content.transfers().stream()).forEach(name -> {
                String key = com.techeazyhq.cm.cnscmd.dom.Processor.key(name);
                com.techeazyhq.cm.cnscmd.dom.Processor p = byKey.get(key);
                if (p != null && !out.containsKey(key)) {
                    out.put(key, new PrpsParty(name.strip(), p.role(), p.country()));
                }
            });
            return List.copyOf(out.values());
        });
    }

    private Mono<PrpsDef> change(String dfId, String code, Function<PrpsDef, Mono<Void>> action) {
        return found(dfId, code).flatMap(def -> invalidAs400(() -> action.apply(def))
                .flatMap(recorded -> recorded)
                .then(repo.save(def)));
    }

    private Mono<PrpsDef> found(String dfId, String code) {
        return repo.findByCode(dfId, code)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(404, "purpose-not-found",
                        "There is no purpose " + code)));
    }

    /** The domain refusing what was asked is the caller's mistake, not a server error. */
    private static <T> Mono<T> invalidAs400(java.util.function.Supplier<T> action) {
        try {
            return Mono.just(action.get());
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-purpose", e.getMessage()));
        } catch (IllegalStateException e) {
            return Mono.error(new DomRuleViolation(409, "no-draft", e.getMessage()));
        }
    }
}
