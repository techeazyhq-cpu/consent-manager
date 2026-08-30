package com.techeazyhq.cm.cnscmd.ifc.rest;

import java.util.Set;

import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;

/** Request body for {@code POST /api/cns-cmd/cns-reqs} — the DF Integration API's core write operation (plan §10). */
public record CnsReqCreateReqDto(
        String dpId,
        ExtDpRefTyp extDpRefTyp,
        String extDpRefVal,
        String prpsCode,
        Set<String> dCatCodes,
        String ntcTitle,
        String ntcText) {
}
