package com.techeazyhq.cm.ntf.app;

import com.techeazyhq.cm.ntf.dom.EmailSenderPort;
import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.NtfMsg;
import com.techeazyhq.cm.ntf.dom.PushSenderPort;
import com.techeazyhq.cm.ntf.dom.SmsSenderPort;
import com.techeazyhq.cm.ntf.dom.WhatsAppSenderPort;

import reactor.core.publisher.Mono;

/**
 * The one fan-out mechanism shared by OTP delivery and consent notifications (plan §9a) —
 * a single Strategy-style dispatch over the channel ports, so neither caller needs to know
 * which adapter handles which channel.
 */
public class NtfDispatchService {

    private final EmailSenderPort emailSenderPort;
    private final SmsSenderPort smsSenderPort;
    private final WhatsAppSenderPort whatsAppSenderPort;
    private final PushSenderPort pushSenderPort;

    public NtfDispatchService(EmailSenderPort emailSenderPort, SmsSenderPort smsSenderPort,
                               WhatsAppSenderPort whatsAppSenderPort, PushSenderPort pushSenderPort) {
        this.emailSenderPort = emailSenderPort;
        this.smsSenderPort = smsSenderPort;
        this.whatsAppSenderPort = whatsAppSenderPort;
        this.pushSenderPort = pushSenderPort;
    }

    public Mono<Void> dispatch(NtfChnl channel, NtfMsg msg) {
        return switch (channel) {
            case EMAIL -> emailSenderPort.send(msg);
            case SMS -> smsSenderPort.send(msg);
            case WHATSAPP -> whatsAppSenderPort.send(msg);
            case PUSH -> pushSenderPort.send(msg);
            case APP -> Mono.empty(); // the DP dashboard read model is the notification itself — no adapter needed
        };
    }
}
