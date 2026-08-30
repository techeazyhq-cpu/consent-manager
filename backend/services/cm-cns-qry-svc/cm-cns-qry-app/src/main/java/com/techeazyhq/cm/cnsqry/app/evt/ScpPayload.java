package com.techeazyhq.cm.cnsqry.app.evt;

import java.util.List;

public record ScpPayload(List<DCatPayload> dCats) {

    public record DCatPayload(String code) {
    }
}
