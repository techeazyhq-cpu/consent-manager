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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.techeazyhq.cm.ntf.dom.DpPrefRepoPort;
import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.NtfCtg;
import com.techeazyhq.cm.ntf.dom.NtfDeliveryRejectedException;
import com.techeazyhq.cm.ntf.dom.NtfMsg;
import com.techeazyhq.cm.ntf.dom.PushDvcRepoPort;

import reactor.core.publisher.Mono;

/**
 * Notifications' step of a principal closing their account (ADR-059): the notice to their email address, which says
 * what happens and what is kept, then the deletion of their push devices. Idempotent: run again, the notice may go out
 * again, with the same reference, and there are no devices left to delete. A notice refused
 * for good (an address that bounces) doesn't hold the erasure up.
 */
public class EraseDpHandler {

    private static final Logger LOG = LoggerFactory.getLogger(EraseDpHandler.class);

    static final String SUBJECT = "Your Consent Manager account is being closed";
    static final String BODY = """
            You asked us to close your Consent Manager account.

            We are withdrawing the consents you had given, telling each organisation, and erasing your contact details, \
            devices and the words of your requests. Your account is then deleted and you will be signed out.

            We keep the record of each consent and its receipts, under an id that no longer leads to you, for seven \
            years, as the law requires of a consent manager (DPDPA, First Schedule). The organisations keep their own \
            records: ask them directly about the data they hold.

            If you didn't ask for this, contact us at once.""";

    private final NtfDispatchService dispatch;
    private final PushDvcRepoPort devices;
    private final DpPrefRepoPort prefs;

    public EraseDpHandler(NtfDispatchService dispatch, PushDvcRepoPort devices) {
        this(dispatch, devices, DpPrefRepoPort.NONE);
    }

    public EraseDpHandler(NtfDispatchService dispatch, PushDvcRepoPort devices, DpPrefRepoPort prefs) {
        this.dispatch = dispatch;
        this.devices = devices;
        this.prefs = prefs;
    }

    public Mono<Map<String, Object>> handle(String erasureId, String dpId, String email) {
        Mono<Boolean> notice = email == null || email.isBlank() ? Mono.just(false)
                : dispatch.dispatch(NtfChnl.EMAIL, new NtfMsg(email, SUBJECT, BODY, NtfCtg.TRANSACTIONAL,
                                "cm-erasure-" + erasureId))
                        .thenReturn(true)
                        .onErrorResume(NtfDeliveryRejectedException.class, e -> {
                            LOG.warn("Erasure {}: the notice was refused ({}); going on", erasureId, e.code());
                            return Mono.just(false);
                        });
        return notice.flatMap(sent -> devices.deleteAllOf(dpId)
                .flatMap(deleted -> prefs.deleteOf(dpId)
                        .map(prefsDeleted -> Map.<String, Object>of("noticeSent", sent, "devicesDeleted", deleted,
                                "preferencesDeleted", prefsDeleted))));
    }
}
