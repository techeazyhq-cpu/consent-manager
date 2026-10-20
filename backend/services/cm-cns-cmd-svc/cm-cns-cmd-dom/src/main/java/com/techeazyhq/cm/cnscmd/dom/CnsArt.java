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

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

import com.techeazyhq.cm.domkit.Agg;

/**
 * The consent-artefact Aggregate Root — the data-blind, signed record of what a Data
 * Principal actually authorized (plan §1). Issued when a {@link CnsReq} is approved;
 * carries the {@code CnsTkn} a Data Fiduciary can validate offline (plan §7c, §9a).
 */
public final class CnsArt extends Agg<String> {

    private final String cnsReqId;
    private final String dfId;
    private final Prps prps;
    private Scp scp;
    private ValPrd valPrd;
    private final Instant issuedAt;
    private DpRef dpRef;
    private CnsArtSts sts;
    private String cnsTkn;

    CnsArt(String id, String cnsReqId, String dfId, DpRef dpRef, Prps prps, Scp scp, ValPrd valPrd,
           CnsArtSts sts, Instant issuedAt, String cnsTkn) {
        super(id);
        this.cnsReqId = cnsReqId;
        this.dfId = dfId;
        this.dpRef = dpRef;
        this.prps = prps;
        this.scp = scp;
        this.valPrd = valPrd;
        this.sts = sts;
        this.issuedAt = issuedAt;
        this.cnsTkn = cnsTkn;
    }

    public String cnsReqId() {
        return cnsReqId;
    }

    public String dfId() {
        return dfId;
    }

    public DpRef dpRef() {
        return dpRef;
    }

    public Prps prps() {
        return prps;
    }

    public Scp scp() {
        return scp;
    }

    public ValPrd valPrd() {
        return valPrd;
    }

    public CnsArtSts sts() {
        return sts;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public String cnsTkn() {
        return cnsTkn;
    }

    /** The token can only be signed once the aggregate (and thus its id) exists — set by the CmdHandler after CnsTknSigner.sign(...). */
    public void attachSignedTkn(String cnsTkn) {
        if (this.cnsTkn != null) {
            throw new IllegalStateException("CnsTkn already attached to CnsArt " + id());
        }
        this.cnsTkn = cnsTkn;
    }

    /** Applied when {@code LinkDpCmdHandler} resolves this artefact's DP identity (plan §7a). */
    public void linkDp(String newDpId) {
        if (dpRef.isLinked()) {
            return; // idempotent, plan §7a
        }
        this.dpRef = DpRef.linked(newDpId);
    }

    public void revoke() {
        requireSts(CnsArtSts.ISSUED, "revoke");
        this.sts = CnsArtSts.REVOKED;
        raise(CnsRevokedEvt.of(id(), dpRef, dfId, prps, cnsReqId));
    }

    /**
     * Ends the consent earlier (ADR-026 §4): a withdrawal scheduled for {@code newUntil}. Verification follows the new
     * end at once, and tokens never outlive it.
     */
    public void shortenTo(Instant newUntil, Instant now) {
        requireInForce(now, "shorten");
        if (!newUntil.isAfter(now)) {
            throw new IllegalArgumentException("choose an end after now; to stop it now, withdraw it");
        }
        if (!newUntil.isBefore(valPrd.until())) {
            throw new IllegalArgumentException("the new end must be before the current one; to extend, renew it");
        }
        this.valPrd = new ValPrd(valPrd.from(), newUntil);
        raise(CnsModifiedEvt.of(this, CnsModifiedEvt.Change.SHORTENED, null, now));
    }

    /**
     * Withdraws one category, keeping the rest (partial withdrawal, ADR-026 §2). Withdrawing the last one is a
     * withdrawal of the whole consent, which {@link #revoke()} records as such.
     */
    public void withdrawCategory(DCat dCat, Instant now) {
        requireInForce(now, "change");
        if (!scp.covers(dCat)) {
            throw new IllegalArgumentException("this consent doesn't cover " + dCat.code());
        }
        if (scp.dCats().size() == 1) {
            throw new IllegalArgumentException(dCat.code() + " is all this consent covers: withdraw the consent instead");
        }
        this.scp = new Scp(scp.dCats().stream().filter(d -> !d.equals(dCat)).collect(Collectors.toSet()));
        raise(CnsModifiedEvt.of(this, CnsModifiedEvt.Change.CATEGORY_WITHDRAWN, dCat.code(), now));
    }

    /**
     * Renews the consent before it lapses (ADR-026 §4): a new validity from now for {@code days}, at most
     * {@code maxDays}, the purpose's maximum. It must end later than the consent does now; to end it sooner, shorten
     * it. A lapsed consent isn't renewed: the fiduciary asks again.
     */
    public void renew(int days, int maxDays, Instant now) {
        requireInForce(now, "renew");
        if (days < 1 || days > maxDays) {
            throw new IllegalArgumentException("consent can last from 1 to " + maxDays + " days for this purpose");
        }
        ValPrd renewed = ValPrd.of(now, Duration.ofDays(days));
        if (!renewed.until().isAfter(valPrd.until())) {
            throw new IllegalArgumentException("a renewal must end later than the consent does now; to end it sooner, "
                    + "shorten it");
        }
        this.valPrd = renewed;
        raise(CnsRenewedEvt.of(this, days, now));
    }

    private void requireInForce(Instant now, String action) {
        if (liveStatus(now) != CnsArtSts.ISSUED) {
            throw new IllegalStateException("Cannot %s a consent that is %s".formatted(action, liveStatus(now)));
        }
    }

    /**
     * The status that governs verification now (ADR-027): an {@code ISSUED} artefact past its validity is already
     * {@code EXPIRED}, whether or not {@code CnsExpirySweepJob} has recorded it yet.
     */
    public CnsArtSts liveStatus(Instant now) {
        return sts == CnsArtSts.ISSUED && valPrd.isExpiredAt(now) ? CnsArtSts.EXPIRED : sts;
    }

    /** Applied by the scheduled {@code CnsExpirySweepJob} (plan §8) for ValPrd-lapsed artefacts. */
    public void expire(Instant now) {
        requireSts(CnsArtSts.ISSUED, "expire");
        if (!valPrd.isExpiredAt(now)) {
            throw new IllegalStateException("CnsArt " + id() + " is not yet past its ValPrd");
        }
        this.sts = CnsArtSts.EXPIRED;
        raise(CnsExpiredEvt.of(this));
    }

    private void requireSts(CnsArtSts required, String action) {
        if (sts != required) {
            throw new IllegalStateException(
                    "Cannot %s a CnsArt in status %s (requires %s)".formatted(action, sts, required));
        }
    }
}
