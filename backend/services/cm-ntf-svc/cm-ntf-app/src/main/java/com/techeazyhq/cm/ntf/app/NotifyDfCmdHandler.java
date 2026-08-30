package com.techeazyhq.cm.ntf.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

/**
 * DF notification stays a Phase-1 logged placeholder (plan §3) — the user's multi-channel
 * request (plan §7c step 2, §9a) was specifically about notifying the *Data Principal*; a DF
 * doesn't yet have a registered notification contact/channel preference in this slice.
 */
public class NotifyDfCmdHandler {

    private static final Logger LOG = LoggerFactory.getLogger(NotifyDfCmdHandler.class);

    public Mono<Void> handle(NotifyDfCmd cmd) {
        return Mono.fromRunnable(() ->
                LOG.info("NotifyDf: dfId={} subjectId={} outcome={}", cmd.dfId(), cmd.subjectId(), cmd.outcome()));
    }
}
