package com.techeazyhq.cm.cnsqry.app.evt;

import java.time.Instant;

public record CnsDpLinkedEvtPayload(String evtId, Instant occurredAt, String recordId, String recordType, String newDpId) {
}
