package com.techeazyhq.cm.cnscmd.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.DpRef;
import com.techeazyhq.cm.cnscmd.dom.NtfCmdPort;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Mono;

/** {@link NtfCmdPort} adapter: publishes NotifyDp/NotifyDf commands to cm-ntf-svc's command topics (plan §7c, §13). */
public class PulsarNtfCmdAdapter implements NtfCmdPort {

    private static final String CTX = "ntf";

    private final PulsarCnsEvtProducer producer;
    private final ObjectMapper objectMapper;

    public PulsarNtfCmdAdapter(PulsarCnsEvtProducer producer, ObjectMapper objectMapper) {
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> notifyDp(CnsReq cnsReq) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(
                        new NotifyDpCmdPayload(cnsReq.id(), cnsReq.dfId(), refKind(cnsReq.dpRef()), refVal(cnsReq.dpRef()),
                                cnsReq.ntcTitle(), cnsReq.ntcText())))
                .flatMap(json -> producer.publish(PulsarTopics.cmd(CTX, "notify-dp"), json));
    }

    @Override
    public Mono<Void> notifyDf(String dfId, String subjectId, String outcome) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(new NotifyDfCmdPayload(dfId, subjectId, outcome)))
                .flatMap(json -> producer.publish(PulsarTopics.cmd(CTX, "notify-df"), json));
    }

    private String refKind(DpRef dpRef) {
        return dpRef instanceof DpRef.LnkdDpRef ? "APP" : ((DpRef.UnlnkDpRef) dpRef).typ().name();
    }

    private String refVal(DpRef dpRef) {
        return dpRef instanceof DpRef.LnkdDpRef lnkd ? lnkd.dpId() : ((DpRef.UnlnkDpRef) dpRef).val();
    }

    private record NotifyDpCmdPayload(
            String cnsReqId, String dfId, String channelHint, String destination, String ntcTitle, String ntcText) {
    }

    private record NotifyDfCmdPayload(String dfId, String subjectId, String outcome) {
    }
}
