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
