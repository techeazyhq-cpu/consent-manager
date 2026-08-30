package com.techeazyhq.cm.cnsqry.app.evt;

import java.time.Instant;

public record CnsReqCreatedEvtPayload(
        String evtId, Instant occurredAt, String cnsReqId, String dfId, DpRefPayload dpRef,
        PrpsPayload prps, ScpPayload scp) {

    public record PrpsPayload(String code) {
    }
}
