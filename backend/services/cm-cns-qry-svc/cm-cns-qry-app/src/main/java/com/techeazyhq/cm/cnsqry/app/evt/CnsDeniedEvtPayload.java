package com.techeazyhq.cm.cnsqry.app.evt;

import java.time.Instant;

public record CnsDeniedEvtPayload(String evtId, Instant occurredAt, String cnsReqId, String dfId, DpRefPayload dpRef) {
}
