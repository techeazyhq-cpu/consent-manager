package com.techeazyhq.cm.ntf.app;

import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.NtfCtg;
import com.techeazyhq.cm.ntf.dom.NtfMsg;

import reactor.core.publisher.Mono;

public class NotifyDpCmdHandler {

    private final NtfDispatchService dispatchService;

    public NotifyDpCmdHandler(NtfDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    public Mono<Void> handle(NotifyDpCmd cmd) {
        NtfChnl channel = toChannel(cmd.channelHint());
        NtfMsg msg = new NtfMsg(cmd.destination(), cmd.ntcTitle(), cmd.ntcText(), NtfCtg.TRANSACTIONAL,
                "cm-notify-dp-" + cmd.cnsReqId());
        return dispatchService.dispatch(channel, msg);
    }

    private NtfChnl toChannel(String channelHint) {
        return switch (channelHint) {
            case "EMAIL" -> NtfChnl.EMAIL;
            case "PHONE" -> NtfChnl.SMS;
            default -> NtfChnl.APP;
        };
    }
}
