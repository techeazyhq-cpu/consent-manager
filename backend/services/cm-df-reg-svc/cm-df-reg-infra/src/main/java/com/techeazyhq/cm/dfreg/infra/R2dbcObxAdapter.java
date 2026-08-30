package com.techeazyhq.cm.dfreg.infra;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dfreg.dom.ObxPort;
import com.techeazyhq.cm.domkit.DomainEvt;

import reactor.core.publisher.Mono;

public class R2dbcObxAdapter implements ObxPort {

    private final ObxWriter writer;

    public R2dbcObxAdapter(DatabaseClient db, ObjectMapper objectMapper) {
        this.writer = new ObxWriter(db, objectMapper);
    }

    @Override
    public Mono<Void> enqueue(DomainEvt evt, String aggType, String aggId) {
        return writer.insert(evt, aggType, aggId);
    }
}
