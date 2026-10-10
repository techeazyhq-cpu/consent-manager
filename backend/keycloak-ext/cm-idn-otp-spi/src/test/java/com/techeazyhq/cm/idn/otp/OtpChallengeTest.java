package com.techeazyhq.cm.idn.otp;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpChallengeTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

    @Test
    void issuesASixDigitCodeAndKeepsOnlyASaltedHashOfIt() {
        OtpChallenge.Issued issued = OtpChallenge.issue(NOW, FIVE_MINUTES, 0);

        assertTrue(issued.code().matches("[0-9]{6}"));
        assertFalse(issued.challenge().codeHash().contains(issued.code()));
        assertEquals(NOW.plus(FIVE_MINUTES), issued.challenge().expiresAt());
        assertEquals(1, issued.challenge().sends());
        Map<String, String> notes = new HashMap<>();
        issued.challenge().writeTo(notes::put);
        assertFalse(notes.containsValue(issued.code()));
    }

    @Test
    void theRightCodeIsAcceptedEvenWithSpacesAndAWrongOneIsNot() {
        OtpChallenge.Issued issued = OtpChallenge.issue(NOW, FIVE_MINUTES, 0);
        String spaced = issued.code().substring(0, 3) + " " + issued.code().substring(3);

        assertEquals(OtpChallenge.Verdict.ACCEPTED, issued.challenge().verify(spaced, NOW.plusSeconds(10), 5));
        assertEquals(OtpChallenge.Verdict.WRONG, issued.challenge().verify(otherThan(issued.code()), NOW, 5));
        assertEquals(OtpChallenge.Verdict.WRONG, issued.challenge().verify(null, NOW, 5));
    }

    @Test
    void aCodeStopsWorkingWhenItExpires() {
        OtpChallenge.Issued issued = OtpChallenge.issue(NOW, FIVE_MINUTES, 0);

        assertEquals(OtpChallenge.Verdict.EXPIRED, issued.challenge().verify(issued.code(), NOW.plus(FIVE_MINUTES), 5));
    }

    @Test
    void afterTheMaximumWrongGuessesEvenTheRightCodeIsRefused() {
        OtpChallenge.Issued issued = OtpChallenge.issue(NOW, FIVE_MINUTES, 0);
        OtpChallenge challenge = issued.challenge();
        for (int guess = 0; guess < 5; guess++) {
            assertEquals(OtpChallenge.Verdict.WRONG, challenge.verify(otherThan(issued.code()), NOW, 5));
            challenge = challenge.withFailedAttempt();
        }

        assertEquals(OtpChallenge.Verdict.TOO_MANY_ATTEMPTS, challenge.verify(issued.code(), NOW, 5));
    }

    @Test
    void aNewCodeCanBeSentOnlyAfterTheIntervalAndUpToTheLimit() {
        OtpChallenge first = OtpChallenge.issue(NOW, FIVE_MINUTES, 0).challenge();
        Duration interval = Duration.ofSeconds(30);

        assertFalse(first.mayResend(NOW.plusSeconds(29), interval, 3));
        assertTrue(first.mayResend(NOW.plusSeconds(30), interval, 3));
        OtpChallenge third = OtpChallenge.issue(NOW, FIVE_MINUTES, 2).challenge();
        assertFalse(third.mayResend(NOW.plusSeconds(60), interval, 3));
    }

    @Test
    void survivesTheTripThroughTheAuthenticationSessionNotes() {
        OtpChallenge.Issued issued = OtpChallenge.issue(NOW, FIVE_MINUTES, 1);
        Map<String, String> notes = new HashMap<>();
        issued.challenge().withFailedAttempt().writeTo(notes::put);

        OtpChallenge restored = OtpChallenge.readFrom(notes::get);

        assertEquals(issued.challenge().withFailedAttempt(), restored);
        assertEquals(OtpChallenge.Verdict.ACCEPTED, restored.verify(issued.code(), NOW, 5));
        assertNull(OtpChallenge.readFrom(name -> null));
        assertEquals(notes.keySet(), java.util.Set.of(OtpChallenge.noteNames()));
    }

    private static String otherThan(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }
}
