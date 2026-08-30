package com.techeazyhq.cm.cnscmd.ifc.rest;

import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqSts;

public record CnsReqRespDto(String id, String dfId, CnsReqSts sts, String prpsCode) {
    public static CnsReqRespDto of(CnsReq cnsReq) {
        return new CnsReqRespDto(cnsReq.id(), cnsReq.dfId(), cnsReq.sts(), cnsReq.prps().code());
    }
}
