package com.techeazyhq.cm.ntf.app;

import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.NtfCtg;
import com.techeazyhq.cm.ntf.dom.NtfDeliveryRejectedException;
import com.techeazyhq.cm.ntf.dom.NtfMsg;

import reactor.core.publisher.Mono;

/**
 * Sends an email another service asked for (ADR-060). It is transactional mail (about the sender's own service, not
 * marketing), and an address the mail server refuses for good is dropped by the consumer, not retried
 * ({@code NtfDeliveryRejectedException}).
 */
public class NotifyEmailCmdHandler {

    private final NtfDispatchService dispatch;

    public NotifyEmailCmdHandler(NtfDispatchService dispatch) {
        this.dispatch = dispatch;
    }

    public Mono<Void> handle(NotifyEmailCmd cmd) {
        if (cmd.destination() == null || cmd.destination().isBlank() || cmd.subject() == null || cmd.body() == null) {
            // refused for good: redelivered, it would be refused the same way
            return Mono.error(new NtfDeliveryRejectedException("INVALID_REQUEST",
                    "An email needs an address, a subject and a body"));
        }
        return dispatch.dispatch(NtfChnl.EMAIL,
                new NtfMsg(cmd.destination().trim(), cmd.subject(), cmd.body(), NtfCtg.TRANSACTIONAL, cmd.ref()));
    }
}
