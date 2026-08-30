package com.techeazyhq.cm.ntf.app;

import com.techeazyhq.cm.ntf.dom.NtfChnl;

public record OtpSendCmd(NtfChnl channel, String destination, String code) {
}
