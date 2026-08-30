package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/**
 * Stage 4: Asynchronous Revocation (plan §7c step 5). Data-blind constraint: this event
 * intentionally carries only artefact/purpose identifiers — no DF-internal purge targets
 * (unlike the whitepaper's illustrative {@code cascade_instructions} payload) since CM has
 * no visibility into a DF's downstream stores; each subscriber owns that mapping itself
 * (docs/architecture/adr-0003-pep-pdp-enforcement.md). The producer serializes this to the
 * external wire shape ({@code sub_did}/{@code fiduciary_id}/{@code purpose_code}) — see
 * {@code PulsarCnsEvtProducer} in cm-cns-cmd-infra.
 */
public record CnsRevokedEvt(UUID evtId, Instant occurredAt, String cnsArtId, DpRef dpRef, String dfId, Prps prps)
        implements DomainEvt {

    public static CnsRevokedEvt of(String cnsArtId, DpRef dpRef, String dfId, Prps prps) {
        return new CnsRevokedEvt(UUID.randomUUID(), Instant.now(), cnsArtId, dpRef, dfId, prps);
    }
}
