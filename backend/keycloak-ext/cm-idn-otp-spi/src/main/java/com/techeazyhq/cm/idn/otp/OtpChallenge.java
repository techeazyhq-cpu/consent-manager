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
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * One code sent to one person during one sign-in. Only a salted hash of the code is kept, in the Keycloak
 * authentication session (server-side, gone when the sign-in ends), together with when it expires, how many wrong
 * guesses were made, and how often it was sent.
 */
public record OtpChallenge(String codeHash, String salt, Instant expiresAt, int attempts, Instant sentAt, int sends) {

    public enum Verdict {
        ACCEPTED,
        WRONG,
        EXPIRED,
        TOO_MANY_ATTEMPTS
    }

    /** A freshly issued challenge and the code to deliver; the code itself is never stored. */
    public record Issued(OtpChallenge challenge, String code) {
    }

    static final int CODE_LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String NOTE_PREFIX = "cm.otp.";

    public static Issued issue(Instant now, Duration validity, int previousSends) {
        String code = String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt(1_000_000));
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        return new Issued(new OtpChallenge(hash(salt, code), salt, now.plus(validity), 0, now, previousSends + 1), code);
    }

    /** Checks a guess; a wrong guess is counted by the caller storing {@link #withFailedAttempt()}. */
    public Verdict verify(String guess, Instant now, int maxAttempts) {
        if (attempts >= maxAttempts) {
            return Verdict.TOO_MANY_ATTEMPTS;
        }
        if (!now.isBefore(expiresAt)) {
            return Verdict.EXPIRED;
        }
        String normalized = guess == null ? "" : guess.replaceAll("\\s", "");
        boolean matches = MessageDigest.isEqual(hash(salt, normalized).getBytes(StandardCharsets.US_ASCII),
                codeHash.getBytes(StandardCharsets.US_ASCII));
        return matches ? Verdict.ACCEPTED : Verdict.WRONG;
    }

    public OtpChallenge withFailedAttempt() {
        return new OtpChallenge(codeHash, salt, expiresAt, attempts + 1, sentAt, sends);
    }

    public boolean mayResend(Instant now, Duration interval, int maxSends) {
        return sends < maxSends && !now.isBefore(sentAt.plus(interval));
    }

    public void writeTo(BiConsumer<String, String> notes) {
        notes.accept(NOTE_PREFIX + "hash", codeHash);
        notes.accept(NOTE_PREFIX + "salt", salt);
        notes.accept(NOTE_PREFIX + "expiresAt", expiresAt.toString());
        notes.accept(NOTE_PREFIX + "attempts", Integer.toString(attempts));
        notes.accept(NOTE_PREFIX + "sentAt", sentAt.toString());
        notes.accept(NOTE_PREFIX + "sends", Integer.toString(sends));
    }

    /** @return the stored challenge, or null when this sign-in has none */
    public static OtpChallenge readFrom(Function<String, String> notes) {
        String codeHash = notes.apply(NOTE_PREFIX + "hash");
        if (codeHash == null) {
            return null;
        }
        return new OtpChallenge(codeHash, notes.apply(NOTE_PREFIX + "salt"),
                Instant.parse(notes.apply(NOTE_PREFIX + "expiresAt")),
                Integer.parseInt(notes.apply(NOTE_PREFIX + "attempts")),
                Instant.parse(notes.apply(NOTE_PREFIX + "sentAt")),
                Integer.parseInt(notes.apply(NOTE_PREFIX + "sends")));
    }

    public static String[] noteNames() {
        return new String[] {NOTE_PREFIX + "hash", NOTE_PREFIX + "salt", NOTE_PREFIX + "expiresAt",
                NOTE_PREFIX + "attempts", NOTE_PREFIX + "sentAt", NOTE_PREFIX + "sends"};
    }

    private static String hash(String salt, String code) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest((salt + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
