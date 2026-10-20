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

package com.techeazyhq.cm.ntf.app;

import com.techeazyhq.cm.ntf.dom.DpDeliveryOutcomePort;
import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.NtfCtg;
import com.techeazyhq.cm.ntf.dom.NtfDeliveryRejectedException;
import com.techeazyhq.cm.ntf.dom.NtfMsg;
import com.techeazyhq.cm.ntf.dom.PushDvc;
import com.techeazyhq.cm.ntf.dom.PushDvcRepoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class NotifyDpCmdHandler {

    private static final Logger LOG = LoggerFactory.getLogger(NotifyDpCmdHandler.class);

    static final String PUSH_TITLE = "New consent request";

    private final NtfDispatchService dispatchService;
    private final PushDvcRepoPort pushDvcRepo;
    private final DpDeliveryOutcomePort outcomePort;

    public NotifyDpCmdHandler(NtfDispatchService dispatchService, PushDvcRepoPort pushDvcRepo,
                              DpDeliveryOutcomePort outcomePort) {
        this.dispatchService = dispatchService;
        this.pushDvcRepo = pushDvcRepo;
        this.outcomePort = outcomePort;
    }

    /**
     * An unlinked DP is reached by email or SMS, the only way they learn of the request; if that is refused for good,
     * the failure is reported back to the consent lifecycle and the command completes, since a redelivery would be
     * refused the same way.
     */
    public Mono<Void> handle(NotifyDpCmd cmd) {
        NtfChnl channel = toChannel(cmd.channelHint());
        if (channel == NtfChnl.APP) {
            return pushToDevices(cmd);
        }
        NtfMsg msg = new NtfMsg(cmd.destination(), cmd.ntcTitle(), cmd.ntcText(), NtfCtg.TRANSACTIONAL,
                "cm-notify-dp-" + cmd.cnsReqId());
        return dispatchService.dispatch(channel, msg)
                .onErrorResume(NtfDeliveryRejectedException.class, err -> {
                    LOG.warn("{} to the DP of consent request {} rejected: {}", channel, cmd.cnsReqId(), err.code());
                    return outcomePort.deliveryFailed(cmd.cnsReqId(), channel, err.code());
                });
    }

    /**
     * A linked DP ({@code destination} is their dpId) already sees the request on their dashboard; this also pushes it
     * to every browser and phone they turned notifications on in. Only the notice title goes out — the push travels through
     * third-party push services, so the full notice text stays in the portal and the app, which fetch it after the tap (ADR-032 §4). One device refusing (e.g. an expired
     * token) doesn't stop the others; a transient failure fails the whole command so Pulsar redelivers it, and the
     * per-device ref (the idempotency key) keeps devices already reached from getting it twice.
     */
    private Mono<Void> pushToDevices(NotifyDpCmd cmd) {
        return pushDvcRepo.findByDpId(cmd.destination())
                .concatMap(dvc -> dispatchService.dispatch(NtfChnl.PUSH, pushMsg(cmd, dvc))
                        .onErrorResume(NtfDeliveryRejectedException.class, err -> {
                            LOG.warn("Push to device {} of DP {} rejected: {}", dvc.id(), dvc.dpId(), err.code());
                            return Mono.empty();
                        }))
                .then();
    }

    private NtfMsg pushMsg(NotifyDpCmd cmd, PushDvc dvc) {
        return new NtfMsg(dvc.token(), PUSH_TITLE, cmd.ntcTitle(), NtfCtg.TRANSACTIONAL,
                "cm-notify-dp-" + cmd.cnsReqId() + "-" + dvc.id());
    }

    private NtfChnl toChannel(String channelHint) {
        return switch (channelHint) {
            case "EMAIL" -> NtfChnl.EMAIL;
            case "PHONE" -> NtfChnl.SMS;
            default -> NtfChnl.APP;
        };
    }
}
