package com.techeazyhq.cm.cnsqry.app.evt;

import java.time.Instant;

public record CnsRevokedEvtPayload(String evtId, Instant occurredAt, String cnsArtId, DpRefPayload dpRef, String dfId) {
}
