/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

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
