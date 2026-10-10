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

import com.techeazyhq.cm.ntf.dom.NtfCtg;
import com.techeazyhq.cm.ntf.dom.NtfMsg;

import reactor.core.publisher.Mono;

import java.util.UUID;

/** Delivers a code {@code cm-idn-otp-spi} already generated (plan §9a) — this service never generates or verifies OTPs itself. */
public class OtpSendCmdHandler {

    private final NtfDispatchService dispatchService;

    public OtpSendCmdHandler(NtfDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    public Mono<Void> handle(OtpSendCmd cmd) {
        NtfMsg msg = new NtfMsg(cmd.destination(), "Your CM verification code",
                "Your one-time code is " + cmd.code() + ". It expires shortly.", NtfCtg.OTP, "cm-otp-" + UUID.randomUUID());
        return dispatchService.dispatch(cmd.channel(), msg);
    }
}
