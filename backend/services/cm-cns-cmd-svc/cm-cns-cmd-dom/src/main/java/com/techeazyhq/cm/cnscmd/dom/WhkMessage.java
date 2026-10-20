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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.techeazyhq.cm.domkit.DomainEvt;

/**
 * What a fiduciary's webhook endpoints are told when one of its consents changes (ADR-027 §6.3, ADR-047): ids, states
 * and what the consent now covers, never personal data. The fiduciary reads anything more, such as the record, through
 * the API. Its {@code id} is the domain event's, the same for every endpoint and every retry, so a receiver can
 * de-duplicate.
 */
public record WhkMessage(String id, String type, String dfId, Instant occurredAt, Map<String, Object> data) {

    /** Every type an endpoint can subscribe to. */
    public static final List<String> TYPES = List.of(
            "consent.requested", "consent.notification_failed", "consent.given", "consent.refused",
            "consent.modified", "consent.renewed", "consent.withdrawn", "consent.lapsed", "receipt.issued");

    /** Sent on request, so a fiduciary can check its endpoint and its signature checks. Not subscribable. */
    public static final String TEST = "webhook.test";

    /** The message for {@code evt}, or empty for events no fiduciary is told about. */
    public static Optional<WhkMessage> of(DomainEvt evt) {
        return Optional.ofNullable(switch (evt) {
            case CnsReqCreatedEvt e -> msg(e, "consent.requested", e.dfId(), data(e.cnsReqId(), null, e.prps(),
                    e.scp(), null, "dpv:ConsentRequested"), m -> {
                        if (e.prpsVer() != null) {
                            m.put("prpsVer", e.prpsVer().verNo());
                        }
                    });
            case CnsNotificationFailedEvt e -> msg(e, "consent.notification_failed", e.dfId(),
                    data(e.cnsReqId(), null, null, null, null, "dpv:ConsentRequested"), m -> { });
            case CnsGrantedEvt e -> msg(e, "consent.given", e.dfId(),
                    data(e.cnsReqId(), e.cnsArtId(), null, e.scp(), e.valPrd(), "dpv:ConsentGiven"), m -> { });
            case CnsDeniedEvt e -> msg(e, "consent.refused", e.dfId(),
                    data(e.cnsReqId(), null, null, null, null, "dpv:ConsentRefused"), m -> { });
            case CnsModifiedEvt e -> msg(e, "consent.modified", e.dfId(),
                    data(e.cnsReqId(), e.cnsArtId(), e.prps(), e.scp(), e.valPrd(), "dpv:ConsentGiven"),
                    m -> {
                        m.put("change", e.change().name());
                        if (e.dCat() != null) {
                            m.put("dCatWithdrawn", e.dCat());
                        }
                    });
            case CnsRenewedEvt e -> msg(e, "consent.renewed", e.dfId(),
                    data(e.cnsReqId(), e.cnsArtId(), e.prps(), e.scp(), e.valPrd(), "dpv:RenewedConsentGiven"), m -> { });
            case CnsRevokedEvt e -> msg(e, "consent.withdrawn", e.dfId(),
                    data(e.cnsReqId(), e.cnsArtId(), e.prps(), null, null, "dpv:ConsentWithdrawn"), m -> { });
            case CnsExpiredEvt e -> msg(e, "consent.lapsed", e.dfId(),
                    data(e.cnsReqId(), e.cnsArtId(), null, null, null, "dpv:ConsentExpired"),
                    m -> m.put("lapsedAt", e.lapsedAt() == null ? null : e.lapsedAt().toString()));
            case CnsRcptIssuedEvt e -> msg(e, "receipt.issued", e.dfId(), data(e.cnsReqId(), null, null, null, null, null),
                    m -> {
                        m.remove("status");
                        m.put("receiptId", e.rcptId());
                        m.put("forEvent", e.seq());
                        m.put("sha256", e.sha256());
                    });
            default -> null;
        });
    }

    private static WhkMessage msg(DomainEvt e, String type, String dfId, Map<String, Object> data,
                                  java.util.function.Consumer<Map<String, Object>> more) {
        if (dfId == null) {
            return null;
        }
        more.accept(data);
        return new WhkMessage(e.evtId().toString(), type, dfId, e.occurredAt(), data);
    }

    private static Map<String, Object> data(String cnsReqId, String cnsArtId, Prps prps, Scp scp, ValPrd valPrd,
                                            String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cnsReqId", cnsReqId);
        if (cnsArtId != null) {
            m.put("cnsArtId", cnsArtId);
        }
        if (prps != null) {
            m.put("prpsCode", prps.code());
        }
        m.put("status", status);
        if (scp != null) {
            m.put("dCatCodes", scp.dCats().stream().map(DCat::code).sorted().toList());
        }
        if (valPrd != null) {
            m.put("validFrom", valPrd.from().toString());
            m.put("validUntil", valPrd.until().toString());
        }
        return m;
    }

    /** The JSON body as a map: {@code id}, {@code type}, {@code occurredAt}, {@code data}. */
    public Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("type", type);
        body.put("occurredAt", occurredAt.toString());
        body.put("data", data);
        return body;
    }
}
