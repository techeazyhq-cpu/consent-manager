package com.techeazyhq.cm.cnscmd.ifc.rest;

import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtSts;

public record CnsArtRespDto(String id, String cnsReqId, String dfId, CnsArtSts sts, String cnsTkn) {
    public static CnsArtRespDto of(CnsArt cnsArt) {
        return new CnsArtRespDto(cnsArt.id(), cnsArt.cnsReqId(), cnsArt.dfId(), cnsArt.sts(), cnsArt.cnsTkn());
    }
}
