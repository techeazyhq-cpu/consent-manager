package com.techeazyhq.cm.dfreg.dom;

import java.time.Instant;

import com.techeazyhq.cm.domkit.Agg;

/** The Data Fiduciary Aggregate Root (plan §3). */
public final class Df extends Agg<String> {

    private final String name;
    private final String contactEmail;
    private final Instant registeredAt;
    private DfSts sts;
    private String cmDfApiClientSecret;
    private Instant activatedAt;

    Df(String id, String name, String contactEmail, DfSts sts, Instant registeredAt,
       String cmDfApiClientSecret, Instant activatedAt) {
        super(id);
        this.name = name;
        this.contactEmail = contactEmail;
        this.sts = sts;
        this.registeredAt = registeredAt;
        this.cmDfApiClientSecret = cmDfApiClientSecret;
        this.activatedAt = activatedAt;
    }

    public String name() {
        return name;
    }

    public String contactEmail() {
        return contactEmail;
    }

    public DfSts sts() {
        return sts;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public String cmDfApiClientSecret() {
        return cmDfApiClientSecret;
    }

    public Instant activatedAt() {
        return activatedAt;
    }

    /** {@code clientSecret} comes from Keycloak Admin API provisioning, performed by -infra before this is called (plan §10). */
    public void activate(String clientSecret) {
        if (sts != DfSts.PENDING) {
            throw new IllegalStateException("Cannot activate a DF in status " + sts);
        }
        this.sts = DfSts.ACTIVE;
        this.cmDfApiClientSecret = clientSecret;
        this.activatedAt = Instant.now();
        raise(DfActivatedEvt.of(id(), name));
    }
}
