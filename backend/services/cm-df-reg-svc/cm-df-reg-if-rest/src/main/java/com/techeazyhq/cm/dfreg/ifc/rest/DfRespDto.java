package com.techeazyhq.cm.dfreg.ifc.rest;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfSts;

public record DfRespDto(String id, String name, DfSts sts, String cmDfApiClientSecret) {
    public static DfRespDto of(Df df) {
        return new DfRespDto(df.id(), df.name(), df.sts(), df.cmDfApiClientSecret());
    }
}
