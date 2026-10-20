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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.techeazyhq.cm.domkit.DomainEvt;

/**
 * What each change of a consent writes to its 27560 record (ADR-025). Every change already raises a domain event in
 * its own transaction; recording from that event, in that transaction, means the record can never disagree with the
 * consent, and no handler can forget to record.
 */
public final class CnsRecording {

    private CnsRecording() {
    }

    /** What to write for one domain event. */
    public sealed interface Entry {
        String cnsReqId();
    }

    /** A new record: the request was raised. */
    public record Open(String cnsReqId, CnsRec header, CnsRecEvt first) implements Entry {
    }

    /** One more event in the request's record. */
    public record Append(String cnsReqId, CnsRecEvt evt) implements Entry {
    }

    /** The request's principal became known: the record's subject, pseudonymous. */
    public record Subject(String cnsReqId, String dpId) implements Entry {
    }

    /** The entry for {@code evt}, or empty for events that aren't part of a consent's record. */
    public static Optional<Entry> of(DomainEvt evt) {
        return Optional.ofNullable(switch (evt) {
            case CnsReqCreatedEvt e -> new Open(e.cnsReqId(),
                    new CnsRec(UUID.randomUUID().toString(), e.cnsReqId(), e.dfId(), dpIdOf(e.dpRef()), e.prps().code(),
                            e.prpsVer(), e.occurredAt(), null),
                    evt(e, CnsRecEvt.Type.REQUESTED, CnsRecEvt.Actor.FIDUCIARY, codes(e.scp()), null, null));
            case CnsGrantedEvt e -> new Append(e.cnsReqId(), evt(e, CnsRecEvt.Type.GIVEN, CnsRecEvt.Actor.PRINCIPAL,
                    codes(e.scp()), e.valPrd(), null));
            case CnsDeniedEvt e -> new Append(e.cnsReqId(),
                    evt(e, CnsRecEvt.Type.REFUSED, CnsRecEvt.Actor.PRINCIPAL, null, null, null));
            case CnsModifiedEvt e -> new Append(e.cnsReqId(), evt(e, CnsRecEvt.Type.MODIFIED, CnsRecEvt.Actor.PRINCIPAL,
                    codes(e.scp()), e.valPrd(), e.change() == CnsModifiedEvt.Change.CATEGORY_WITHDRAWN
                            ? e.change() + ":" + e.dCat() : e.change().name()));
            case CnsRenewedEvt e -> new Append(e.cnsReqId(), evt(e, CnsRecEvt.Type.RENEWED, CnsRecEvt.Actor.PRINCIPAL,
                    codes(e.scp()), e.valPrd(), null));
            case CnsRevokedEvt e -> e.cnsReqId() == null ? null : new Append(e.cnsReqId(),
                    evt(e, CnsRecEvt.Type.WITHDRAWN, CnsRecEvt.Actor.PRINCIPAL, null, null, null));
            case CnsExpiredEvt e -> e.cnsReqId() == null ? null : new Append(e.cnsReqId(),
                    evt(e, CnsRecEvt.Type.LAPSED, CnsRecEvt.Actor.PLATFORM, null, null, null));
            case CnsDpLinkedEvt e -> "CnsReq".equals(e.recordType()) ? new Subject(e.recordId(), e.newDpId()) : null;
            default -> null;
        });
    }

    private static CnsRecEvt evt(DomainEvt e, CnsRecEvt.Type type, CnsRecEvt.Actor actor, Set<String> dCats,
                                 ValPrd valPrd, String detail) {
        return new CnsRecEvt(0, e.evtId(), e.occurredAt(), type, actor, dCats,
                valPrd == null ? null : valPrd.from(), valPrd == null ? null : valPrd.until(), detail);
    }

    private static Set<String> codes(Scp scp) {
        return scp == null ? null : scp.dCats().stream().map(DCat::code).collect(Collectors.toSet());
    }

    private static String dpIdOf(DpRef ref) {
        return ref instanceof DpRef.LnkdDpRef linked ? linked.dpId() : null;
    }
}
