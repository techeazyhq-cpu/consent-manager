package com.techeazyhq.cm.cnsqry.app.evt;

import java.time.Instant;

public record CnsExpiredEvtPayload(String evtId, Instant occurredAt, String cnsArtId) {
}
