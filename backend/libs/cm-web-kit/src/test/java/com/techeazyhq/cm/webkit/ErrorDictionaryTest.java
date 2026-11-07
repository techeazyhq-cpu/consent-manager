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

package com.techeazyhq.cm.webkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The dictionary as shipped (ADR-062). {@code scripts/error-dictionary.mjs} checks it against the source in CI; these
 * check what the services load at runtime, so a malformed file stops the build, not the first request that needs it.
 */
class ErrorDictionaryTest {

    private static final Pattern PROBLEM_CODE = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    private final ErrorDictionary dictionary = ErrorDictionary.instance();

    @Test
    void everyProblemIsWellFormed() {
        Set<String> seen = new HashSet<>();
        long problems = 0;
        for (ErrorDictionary.Entry e : dictionary.entries()) {
            assertTrue(seen.add(e.code()), e.code() + " is listed twice");
            assertFalse(e.title().isBlank() || e.description().isBlank() || e.action().isBlank(), e.code() + " has an empty text");
            assertTrue(e.title().length() <= 60 && !e.title().endsWith("."), e.code() + ": titles are short and have no full stop");
            assertFalse(e.raisedBy().isEmpty(), e.code() + " says who raises it");
            if (e.kind().equals("problem")) {
                problems++;
                assertTrue(PROBLEM_CODE.matcher(e.code()).matches(), e.code() + " is lower-case words joined by hyphens");
                assertTrue(e.status() >= 400 && e.status() <= 599, e.code() + " has a status of 400 to 599");
                if (e.retryable()) {
                    assertTrue(e.status() == 429 || e.status() == 503, e.code() + ": only 429 and 503 are retried as they are");
                }
            }
        }
        assertTrue(problems >= 100, "the dictionary lists the platform's problems, not a sample: " + problems);
    }

    @Test
    void aCodeHasOneTitleAndOneStatusWhoeverRaisesIt() {
        ErrorDictionary.Entry quota = dictionary.require("quota-exceeded");

        assertEquals("Monthly allowance used up", quota.title());
        assertEquals(429, quota.status());
        assertTrue(quota.retryable());
        assertEquals(403, dictionary.require("df-not-active").status());
        assertEquals(409, dictionary.require("df-activation-required").status(), "a different situation is a different code");
        assertEquals(404, dictionary.require("draft-not-found").status());
        assertEquals(409, dictionary.require("no-draft").status());
    }

    @Test
    void anUnlistedCodeStillGetsATitleAndIsNeverRetriedBlind() {
        assertFalse(dictionary.find("not-a-real-code").isPresent());
        assertEquals("Not a real code", dictionary.title("not-a-real-code"));
        assertFalse(dictionary.retryable("not-a-real-code", 409));
        assertTrue(dictionary.retryable("not-a-real-code", 503));
        assertEquals(418, dictionary.status("not-a-real-code", 418));
        assertThrows(IllegalArgumentException.class, () -> dictionary.require("not-a-real-code"));
    }

    @Test
    void theFileIsRefusedIfACodeIsListedTwice() throws Exception {
        String twice = "{\"entries\":[{\"code\":\"a\",\"kind\":\"problem\"},{\"code\":\"a\",\"kind\":\"problem\"}]}";

        assertThrows(IllegalStateException.class, () -> ErrorDictionary.parse(new ObjectMapper().readTree(twice)));
    }

    @Test
    void logCodesAndEventsCarryALevelNotAStatus() {
        ErrorDictionary.Entry stuck = dictionary.require("CM-ERASURE-STUCK");

        assertEquals("log", stuck.kind());
        assertEquals("ERROR", stuck.level());
        assertEquals(null, stuck.status());
        assertEquals("event", dictionary.require("cm_otp_rate_limited").kind());
    }
}
