/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */


package com.techeazyhq.cm.dfreg.dom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * An organisation's application on the public sign-up page, until its contact address is confirmed (ADR-065). The token that
 * proves the address is mailed once; only its SHA-256 is kept. Confirming turns the application into a fiduciary waiting
 * for staff to activate it. A token works once and only before it expires.
 */
public final class DfSignup {

    public enum Status { AWAITING_EMAIL, VERIFIED }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String id;
    private final String name;
    private final String contactEmail;
    private final String legalName;
    private final String industry;
    private final String website;
    private final String tokenHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Status status;
    private Instant verifiedAt;
    private String dfId;

    DfSignup(String id, String name, String contactEmail, String legalName, String industry, String website, String tokenHash,
             Instant createdAt, Instant expiresAt, Status status, Instant verifiedAt, String dfId) {
        this.id = id;
        this.name = name;
        this.contactEmail = contactEmail;
        this.legalName = legalName;
        this.industry = industry;
        this.website = website;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.verifiedAt = verifiedAt;
        this.dfId = dfId;
    }

    /** A new application and the token to mail, in the clear for this one time. */
    public record Issued(DfSignup signup, String token) {
    }

    public static Issued create(String name, String contactEmail, String legalName, String industry, String website, Instant now,
                                Duration ttl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        DfSignup s = new DfSignup(UUID.randomUUID().toString(), name, contactEmail, blankToNull(legalName), blankToNull(industry),
                blankToNull(website), hash(token), now, now.plus(ttl), Status.AWAITING_EMAIL, null, null);
        return new Issued(s, token);
    }

    public static DfSignup rehydrate(String id, String name, String contactEmail, String legalName, String industry, String website,
                                     String tokenHash, Instant createdAt, Instant expiresAt, Status status, Instant verifiedAt, String dfId) {
        return new DfSignup(id, name, contactEmail, legalName, industry, website, tokenHash, createdAt, expiresAt, status, verifiedAt, dfId);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Whether {@code token} is the one mailed, compared in constant time. */
    public boolean matches(String token) {
        if (token == null) {
            return false;
        }
        return MessageDigest.isEqual(tokenHash.getBytes(StandardCharsets.UTF_8), hash(token).getBytes(StandardCharsets.UTF_8));
    }

    /** Usable: not already confirmed and not expired. */
    public boolean usable(Instant now) {
        return status == Status.AWAITING_EMAIL && now.isBefore(expiresAt);
    }

    public void verified(String dfId, Instant at) {
        if (status != Status.AWAITING_EMAIL) {
            throw new IllegalStateException("This application was already confirmed");
        }
        this.status = Status.VERIFIED;
        this.verifiedAt = at;
        this.dfId = dfId;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String contactEmail() {
        return contactEmail;
    }

    public String legalName() {
        return legalName;
    }

    public String industry() {
        return industry;
    }

    public String website() {
        return website;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Status status() {
        return status;
    }

    public Instant verifiedAt() {
        return verifiedAt;
    }

    public String dfId() {
        return dfId;
    }
}
