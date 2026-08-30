package com.techeazyhq.cm.dfreg.dom;

import java.time.Instant;
import java.util.UUID;

public final class DfFactory {

    private DfFactory() {
    }

    public static Df register(String name, String contactEmail) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (contactEmail == null || contactEmail.isBlank()) {
            throw new IllegalArgumentException("contactEmail must not be blank");
        }
        return new Df(UUID.randomUUID().toString(), name, contactEmail, DfSts.PENDING, Instant.now(), null, null);
    }

    public static Df rehydrate(String id, String name, String contactEmail, DfSts sts, Instant registeredAt,
                                String cmDfApiClientSecret, Instant activatedAt) {
        return new Df(id, name, contactEmail, sts, registeredAt, cmDfApiClientSecret, activatedAt);
    }
}
