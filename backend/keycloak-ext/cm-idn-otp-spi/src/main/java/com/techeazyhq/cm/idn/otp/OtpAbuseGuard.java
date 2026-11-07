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

package com.techeazyhq.cm.idn.otp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Limits across sign-ins (ADR-061). A sign-in's own limits (the code's life, its wrong guesses, its resends) live in the
 * session and start again with every sign-in page, which left two holes: anyone could make the platform send codes to
 * any address or number as often as they liked, and a fresh sign-in gave a fresh code and fresh guesses. These limits
 * live in Keycloak's shared single-use cache, so they hold across sign-ins, realms and Keycloak nodes:
 * <ul>
 *   <li><b>codes sent to one address</b>, per hour and per day;</li>
 *   <li><b>codes requested from one network address</b>, per hour, so one host can't spread the same attack over many
 *       addresses. Generous, since mobile carriers and offices put many real people behind one address; the limits per
 *       address are what protect a person, this one only bounds the cost of a single host;</li>
 *   <li><b>wrong guesses for one address</b>, per hour and per day, whether or not it has an account: Keycloak's brute
 *       force protection only knows existing users, and a new principal signs up by signing in.</li>
 * </ul>
 * They apply the same way to an address with an account and one without, so a refusal reveals nothing about who is
 * registered. Counting is exact: each event claims a numbered slot that expires after its window, with an atomic
 * put-if-absent, so replicas racing can't both take the last one.
 */
public final class OtpAbuseGuard {

    /** At most {@code max} events within any {@code window}; a {@code max} of 0 switches the limit off. */
    public record Limit(int max, Duration window) {
    }

    public record Limits(List<Limit> sendsPerAddress, List<Limit> sendsPerNetwork, List<Limit> wrongGuessesPerAddress) {

        public static Limits defaults() {
            return new Limits(
                    List.of(new Limit(5, Duration.ofHours(1)), new Limit(10, Duration.ofDays(1))),
                    List.of(new Limit(300, Duration.ofHours(1))),
                    List.of(new Limit(10, Duration.ofHours(1)), new Limit(20, Duration.ofDays(1))));
        }
    }

    /** The shared store. Claiming is atomic and a claim expires by itself after {@code ttl}. */
    public interface Slots {

        /** Takes {@code key} for {@code ttl}; false when it is already taken. */
        boolean claim(String key, Duration ttl);

        boolean taken(String key);

        void release(String key);
    }

    public enum Denial {
        /** Too many wrong guesses for this address lately: neither a code nor a guess is accepted. */
        TOO_MANY_WRONG_GUESSES,
        /** Too many codes sent to this address lately. */
        TOO_MANY_CODES,
        /** Too many codes requested from this network address lately. */
        NETWORK_BUSY
    }

    /** What a send was admitted with; given back to {@link #release} if nothing ended up being sent. */
    public record Permit(List<String> claimed) {
    }

    /** Admitted with a {@link Permit}, or refused for {@link Denial}. */
    public record Admission(Permit permit, Denial denial) {

        public boolean admitted() {
            return denial == null;
        }
    }

    private final Slots slots;
    private final Limits limits;

    public OtpAbuseGuard(Slots slots, Limits limits) {
        this.slots = slots;
        this.limits = limits;
    }

    /**
     * Whether a code may be sent to {@code to}, requested from {@code network} (null when unknown). Claims the
     * allowance as it goes, and gives back everything it took when it refuses.
     */
    public Admission admitSend(OtpIdentifier to, String network) {
        if (wrongGuessesExhausted(to)) {
            return new Admission(null, Denial.TOO_MANY_WRONG_GUESSES);
        }
        List<String> claimed = new ArrayList<>();
        if (!claimAll(limits.sendsPerAddress(), "send", addressKey(to), claimed)) {
            releaseAll(claimed);
            return new Admission(null, Denial.TOO_MANY_CODES);
        }
        if (network != null && !network.isBlank()
                && !claimAll(limits.sendsPerNetwork(), "net", digest(network.trim()), claimed)) {
            releaseAll(claimed);
            return new Admission(null, Denial.NETWORK_BUSY);
        }
        return new Admission(new Permit(List.copyOf(claimed)), null);
    }

    /** Nothing was sent after all (cm-ntf-svc refused or was down): the person shouldn't pay for it. */
    public void release(Permit permit) {
        if (permit != null) {
            releaseAll(permit.claimed());
        }
    }

    /** Whether the address has used up its wrong guesses, in any window. */
    public boolean wrongGuessesExhausted(OtpIdentifier to) {
        String address = addressKey(to);
        for (Limit limit : limits.wrongGuessesPerAddress()) {
            if (limit.max() > 0 && takenCount(limit, "guess", address) >= limit.max()) {
                return true;
            }
        }
        return false;
    }

    /** A wrong code was entered for {@code to}: counted against every window, until each is full. */
    public void recordWrongGuess(OtpIdentifier to) {
        String address = addressKey(to);
        for (Limit limit : limits.wrongGuessesPerAddress()) {
            if (limit.max() > 0) {
                claimOne(limit, "guess", address);
            }
        }
    }

    private boolean claimAll(List<Limit> list, String scope, String id, List<String> claimed) {
        for (Limit limit : list) {
            if (limit.max() <= 0) {
                continue;
            }
            String key = claimOne(limit, scope, id);
            if (key == null) {
                return false;
            }
            claimed.add(key);
        }
        return true;
    }

    /** The first free numbered slot, claimed; null when all {@code max} are taken. */
    private String claimOne(Limit limit, String scope, String id) {
        for (int n = 1; n <= limit.max(); n++) {
            String key = slotKey(limit, scope, id, n);
            if (slots.claim(key, limit.window())) {
                return key;
            }
        }
        return null;
    }

    private int takenCount(Limit limit, String scope, String id) {
        int taken = 0;
        for (int n = 1; n <= limit.max(); n++) {
            if (slots.taken(slotKey(limit, scope, id, n))) {
                taken++;
            }
        }
        return taken;
    }

    private void releaseAll(List<String> keys) {
        keys.forEach(slots::release);
    }

    private static String slotKey(Limit limit, String scope, String id, int n) {
        return "cm-otp:" + scope + ":" + id + ":" + limit.window().toSeconds() + ":" + n;
    }

    /**
     * What an address is counted as: a hash, so no address sits in the cache, and for email without the {@code +tag}
     * in front of the {@code @}, since {@code a+1@x.com} and {@code a+2@x.com} reach the same mailbox. Realms share it.
     */
    static String addressKey(OtpIdentifier to) {
        String value = to.value();
        if (to.kind() == OtpIdentifier.Kind.EMAIL) {
            int at = value.indexOf('@');
            int plus = value.indexOf('+');
            if (plus > 0 && plus < at) {
                value = value.substring(0, plus) + value.substring(at);
            }
        }
        return digest(to.kind().name().toLowerCase(Locale.ROOT) + ":" + value);
    }

    private static String digest(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
