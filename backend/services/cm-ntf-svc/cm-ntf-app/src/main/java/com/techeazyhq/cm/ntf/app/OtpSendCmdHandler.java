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

import java.util.Locale;
import java.util.UUID;

import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.NtfCtg;
import com.techeazyhq.cm.ntf.dom.NtfMsg;
import com.techeazyhq.cm.ntf.dom.OtpSendLedgerPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Delivers a code {@code cm-idn-otp-spi} already generated (plan §9a) — this service never generates or verifies OTPs itself.
 * It does keep a limit of its own on what it will send (ADR-080): the sign-in's limits (ADR-061) are in Keycloak, in memory, and
 * count only what Keycloak asks for, whereas this is where the message is paid for and reaches a person.
 */
public class OtpSendCmdHandler {

    private final NtfDispatchService dispatchService;
    private final OtpSendLedgerPort ledger;
    private final EmailFingerprint fingerprint;
    private final OtpSendLedgerPort.Limits limits;

    /** No limits of its own: for tests of delivery alone. */
    public OtpSendCmdHandler(NtfDispatchService dispatchService) {
        this(dispatchService, null, null, null);
    }

    public OtpSendCmdHandler(NtfDispatchService dispatchService, OtpSendLedgerPort ledger, EmailFingerprint fingerprint,
                             OtpSendLedgerPort.Limits limits) {
        this.dispatchService = dispatchService;
        this.ledger = ledger;
        this.fingerprint = fingerprint;
        this.limits = limits;
    }

    public Mono<Void> handle(OtpSendCmd cmd) {
        NtfMsg msg = new NtfMsg(cmd.destination(), "Your CM verification code",
                "Your one-time code is " + cmd.code() + ". It expires shortly.", NtfCtg.OTP, "cm-otp-" + UUID.randomUUID());
        if (ledger == null) {
            return dispatchService.dispatch(cmd.channel(), msg);
        }
        return ledger.claim(fingerprint.of(cmd.channel() + ":" + normalise(cmd.channel(), cmd.destination())), cmd.channel(), limits)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(429, "otp-send-limited",
                        "Too many sign-in codes have been sent; try again later")))
                // a code that was not sent does not count: give the place back whatever stopped it
                .flatMap(place -> dispatchService.dispatch(cmd.channel(), msg)
                        .onErrorResume(e -> ledger.release(place).then(Mono.error(e))));
    }

    /**
     * What counts as the same destination: for email, the address without the {@code +tag} before the {@code @}, as the sign-in
     * counts it (ADR-061), because {@code a+1@x.com} and {@code a+2@x.com} are one mailbox; for a number, its digits.
     */
    static String normalise(NtfChnl channel, String destination) {
        String d = destination == null ? "" : destination.trim().toLowerCase(Locale.ROOT);
        if (channel == NtfChnl.EMAIL) {
            int at = d.lastIndexOf('@');
            int plus = at > 0 ? d.lastIndexOf('+', at) : -1;
            return plus > 0 ? d.substring(0, plus) + d.substring(at) : d;
        }
        StringBuilder digits = new StringBuilder();
        for (char c : d.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        return digits.toString();
    }
}
