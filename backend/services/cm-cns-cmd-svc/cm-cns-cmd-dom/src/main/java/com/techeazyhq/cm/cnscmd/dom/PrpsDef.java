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

package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.techeazyhq.cm.domkit.Agg;

/**
 * A purpose a fiduciary asks consent for, with its versions (ADR-031): the Aggregate Root of purpose definitions in
 * the Consent context.
 * <ul>
 *   <li>At most one draft at a time, which may be edited or discarded.</li>
 *   <li>Publishing the draft makes it the current version. Published versions never change.</li>
 *   <li>The platform classifies each new version against the one before: material changes need re-consent.</li>
 *   <li>Requests may only be made from the current version, and only for its data categories (purpose
 *       limitation).</li>
 * </ul>
 */
public final class PrpsDef extends Agg<String> {

    private final String dfId;
    private final String code;
    private final Instant createdAt;
    private final List<PrpsVer> versions;

    PrpsDef(String id, String dfId, String code, Instant createdAt, List<PrpsVer> versions) {
        super(id);
        this.dfId = dfId;
        this.code = code;
        this.createdAt = createdAt;
        this.versions = new ArrayList<>(versions);
        this.versions.sort(Comparator.comparingInt(PrpsVer::verNo));
    }

    /** A new purpose, with its first version as a draft. */
    public static PrpsDef create(String dfId, String code, PrpsVerContent content, Instant now) {
        if (dfId == null || dfId.isBlank()) {
            throw new IllegalArgumentException("dfId must not be blank");
        }
        if (code == null || !PrpsVerContent.CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("a purpose's code is capitals, digits and underscores, starting with "
                    + "a letter, at most 64 characters, such as ORDER_UPDATES");
        }
        content.check();
        PrpsDef def = new PrpsDef(UUID.randomUUID().toString(), dfId, code, now, List.of());
        def.versions.add(new PrpsVer(UUID.randomUUID().toString(), 1, PrpsVer.Sts.DRAFT, content, null, now, null));
        return def;
    }

    /** Rehydration from persistence: no checks, no events. */
    public static PrpsDef rehydrate(String id, String dfId, String code, Instant createdAt, List<PrpsVer> versions) {
        return new PrpsDef(id, dfId, code, createdAt, versions);
    }

    public String dfId() {
        return dfId;
    }

    public String code() {
        return code;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Every version, oldest first. */
    public List<PrpsVer> versions() {
        return List.copyOf(versions);
    }

    /** The version requests are made from: the latest published. */
    public Optional<PrpsVer> current() {
        return versions.stream().filter(v -> !v.isDraft()).max(Comparator.comparingInt(PrpsVer::verNo));
    }

    public Optional<PrpsVer> draft() {
        return versions.stream().filter(PrpsVer::isDraft).findFirst();
    }

    public Optional<PrpsVer> version(int verNo) {
        return versions.stream().filter(v -> v.verNo() == verNo).findFirst();
    }

    /** Starts a draft of the next version, or replaces the draft's content. */
    public PrpsVer saveDraft(PrpsVerContent content, Instant now) {
        content.check();
        Optional<PrpsVer> draft = draft();
        if (draft.isPresent()) {
            PrpsVer edited = draft.get().withContent(content);
            versions.set(versions.indexOf(draft.get()), edited);
            return edited;
        }
        int next = versions.stream().mapToInt(PrpsVer::verNo).max().orElse(0) + 1;
        PrpsVer started = new PrpsVer(UUID.randomUUID().toString(), next, PrpsVer.Sts.DRAFT, content, null, now, null);
        versions.add(started);
        return started;
    }

    public void discardDraft() {
        PrpsVer draft = draft().orElseThrow(() -> new IllegalStateException("there is no draft to discard"));
        versions.remove(draft);
    }

    /** How the draft differs from the current version: what publishing it would mean. */
    public PrpsChange draftChange() {
        PrpsVer draft = draft().orElseThrow(() -> new IllegalStateException("there is no draft"));
        return current().map(cur -> PrpsChange.between(cur.content(), draft.content())).orElse(PrpsChange.initial());
    }

    /** Publishes the draft as the current version, classified against the one before. */
    public PrpsVer publish(Instant now) {
        PrpsVer draft = draft().orElseThrow(() -> new IllegalStateException("there is no draft to publish"));
        PrpsChange change = draftChange();
        if (change.isNone()) {
            throw new IllegalArgumentException("the draft is the same as version " + current().orElseThrow().verNo()
                    + ": there is nothing to publish");
        }
        PrpsVer published = draft.published(change, now);
        versions.set(versions.indexOf(draft), published);
        raise(PrpsVerPublishedEvt.of(this, published));
        return published;
    }

    /**
     * What a request from published version {@code verNo} asks: its notice in {@code lang} (see
     * {@link PrpsVerContent#notice}) and the categories named, or all of them when none are. A category the version
     * doesn't cover is refused: a request may never ask for more than the purpose says (purpose limitation).
     */
    public CnsReqTemplate templateFor(int verNo, String lang, Collection<String> dCatCodes) {
        PrpsVer ver = version(verNo).filter(v -> !v.isDraft())
                .orElseThrow(() -> new IllegalArgumentException(code + " has no published version " + verNo));
        PrpsNtc notice = ver.content().notice(lang).orElseThrow(() -> new IllegalArgumentException(
                code + " version " + verNo + " has no notice in " + lang + "; it has "
                        + ver.content().notices().stream().map(PrpsNtc::lang).collect(Collectors.joining(", "))));
        Set<String> wanted = dCatCodes == null ? Set.of()
                : dCatCodes.stream().filter(c -> c != null && !c.isBlank()).map(String::strip)
                        .collect(Collectors.toSet());
        List<String> outside = wanted.stream().filter(c -> ver.content().dCat(c).isEmpty()).sorted().toList();
        if (!outside.isEmpty()) {
            throw new IllegalArgumentException(code + " version " + verNo + " doesn't cover " + String.join(", ", outside)
                    + ": a request may only ask for the purpose's own data categories");
        }
        Set<DCat> scope = (wanted.isEmpty()
                ? ver.content().dCats().stream().map(PrpsDCat::code)
                : wanted.stream()).map(DCat::new).collect(Collectors.toSet());
        return CnsReqTemplate.of(new Prps(code), new Scp(scope), notice.title(), notice.text(),
                new PrpsVerRef(ver.id(), ver.verNo(), notice.lang(), notice.hash()));
    }
}
