package com.techeazyhq.cm.cnsqry.app.evt;

import java.time.Instant;

public record CnsGrantedEvtPayload(
        String evtId, Instant occurredAt, String cnsReqId, String cnsArtId, String dfId, DpRefPayload dpRef,
        String cnsTkn, ValPrdPayload valPrd) {

    public record ValPrdPayload(Instant from, Instant until) {
    }
}
