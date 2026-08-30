package com.techeazyhq.cm.ntf.ifc.rest;

import com.techeazyhq.cm.ntf.dom.NtfChnl;

public record OtpSendReqDto(NtfChnl channel, String destination, String code) {
}
