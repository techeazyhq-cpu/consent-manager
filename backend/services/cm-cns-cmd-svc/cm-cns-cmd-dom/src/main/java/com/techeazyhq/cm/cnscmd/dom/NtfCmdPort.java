package com.techeazyhq.cm.cnscmd.dom;

import reactor.core.publisher.Mono;

/** Port to cm-ntf-svc (plan §7c steps 2/4): implemented in -infra as a Pulsar producer to its command topics. */
public interface NtfCmdPort {

    Mono<Void> notifyDp(CnsReq cnsReq);

    /** {@code outcome} is a free-form label (e.g. "GRANTED", "DENIED", "REVOKED") — the notification's own concern, not a write-side status enum. */
    Mono<Void> notifyDf(String dfId, String subjectId, String outcome);
}
