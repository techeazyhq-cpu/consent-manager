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

package com.techeazyhq.cm.ntf.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.ntf.dom.DpPref;
import com.techeazyhq.cm.ntf.dom.DpPrefRepoPort;
import com.techeazyhq.cm.ntf.dom.NtfMsg;
import com.techeazyhq.cm.ntf.dom.PushDvc;
import com.techeazyhq.cm.ntf.dom.PushDvcRepoPort;
import com.techeazyhq.cm.ntf.dom.PushPltf;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DpPreferencesTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");

    private final Map<String, DpPref> stored = new HashMap<>();
    private final List<NtfMsg> emails = new ArrayList<>();
    private final List<NtfMsg> sms = new ArrayList<>();
    private final List<NtfMsg> pushes = new ArrayList<>();
    private final EmailFingerprint fingerprint = new EmailFingerprint("test-key");

    private final DpPrefRepoPort repo = new DpPrefRepoPort() {
        @Override
        public Mono<DpPref> find(String dpId) {
            return Mono.justOrEmpty(stored.get(dpId));
        }

        @Override
        public Mono<DpPref> findByEmailHash(String hash) {
            return Flux.fromIterable(stored.values()).filter(p -> hash.equals(p.emailHash())).next();
        }

        @Override
        public Mono<DpPref> save(DpPref pref) {
            stored.put(pref.dpId(), pref);
            return Mono.just(pref);
        }

        @Override
        public Mono<Long> deleteOf(String dpId) {
            return Mono.just(stored.remove(dpId) == null ? 0L : 1L);
        }
    };

    private final DpPrefCmdHandler prefs = new DpPrefCmdHandler(repo, fingerprint, Clock.fixed(NOW, ZoneOffset.UTC));

    private final PushDvcRepoPort devices = new PushDvcRepoPort() {
        @Override
        public Mono<PushDvc> upsert(String dpId, String token, PushPltf platform) {
            return Mono.just(new PushDvc("d1", dpId, token, platform, NOW, NOW));
        }

        @Override
        public Flux<PushDvc> findByDpId(String dpId) {
            return Flux.just(new PushDvc("d1", dpId, "tok-1", PushPltf.WEB, NOW, NOW));
        }

        @Override
        public Mono<Boolean> delete(String dpId, String id) {
            return Mono.just(true);
        }

        @Override
        public Mono<Long> deleteAllOf(String dpId) {
            return Mono.just(1L);
        }
    };

    private final NtfDispatchService dispatch = new NtfDispatchService(
            msg -> Mono.fromRunnable(() -> emails.add(msg)),
            msg -> Mono.fromRunnable(() -> sms.add(msg)),
            msg -> Mono.error(new AssertionError("no WhatsApp expected")),
            msg -> Mono.fromRunnable(() -> pushes.add(msg)));

    private final NotifyDpCmdHandler notifier = new NotifyDpCmdHandler(dispatch, devices,
            (req, channel, code) -> Mono.empty(), repo, fingerprint);

    private static NotifyDpCmd linked() {
        return new NotifyDpCmd("req-1", "df-1", "APP", "dp-1", "Share your KYC with Acme", "The full notice text");
    }

    private static NotifyDpCmd toEmail(String address) {
        return new NotifyDpCmd("req-2", "df-1", "EMAIL", address, "Share your KYC with Acme", "The full notice text");
    }

    @Test
    void aPrincipalWhoNeverChoseGetsEverything() {
        notifier.handle(linked()).block();
        notifier.handle(toEmail("ann@example.com")).block();

        assertEquals(1, pushes.size());
        assertEquals(1, emails.size());
        assertEquals(new DpPref("dp-1", true, true, null, null), prefs.get("dp-1").block());
    }

    @Test
    void pausingPushStopsEveryDeviceWithoutRemovingThem() {
        prefs.set("dp-1", false, true, "ann@example.com").block();

        notifier.handle(linked()).block();

        assertTrue(pushes.isEmpty());
    }

    @Test
    void turningOffRequestEmailsStopsThemToThatAddressWhateverItsCaseOrSpaces() {
        prefs.set("dp-1", true, false, "Ann@Example.com").block();

        notifier.handle(toEmail("ann@example.com")).block();
        notifier.handle(toEmail("  ANN@example.com ")).block();

        assertTrue(emails.isEmpty(), "the request waits on their dashboard instead");
    }

    @Test
    void anotherAddressIsStillMailedAndSoIsAnAddressWithNoChoice() {
        prefs.set("dp-1", true, false, "ann@example.com").block();

        notifier.handle(toEmail("bob@example.com")).block();

        assertEquals(1, emails.size());
        assertEquals("bob@example.com", emails.get(0).destination());
    }

    @Test
    void turningEmailBackOnMailsAgain() {
        prefs.set("dp-1", true, false, "ann@example.com").block();
        prefs.set("dp-1", true, true, "ann@example.com").block();

        notifier.handle(toEmail("ann@example.com")).block();

        assertEquals(1, emails.size());
    }

    @Test
    void textMessagesAreNotAffectedByTheEmailChoice() {
        prefs.set("dp-1", true, false, "ann@example.com").block();

        notifier.handle(new NotifyDpCmd("req-3", "df-1", "PHONE", "+919876543210", "Title", "Notice")).block();

        assertEquals(1, sms.size());
    }

    @Test
    void theAddressIsNeverKeptOnlyAKeyedHashOfIt() {
        DpPref saved = prefs.set("dp-1", true, false, "ann@example.com").block();

        assertFalse(saved.toString().contains("ann@example.com"));
        assertEquals(64, saved.emailHash().length());
        assertEquals(saved.emailHash(), fingerprint.of("  ANN@example.com"));
        assertNotEquals(saved.emailHash(), new EmailFingerprint("another-key").of("ann@example.com"), "another key gives other hashes");
    }

    @Test
    void someoneWhoSignedInWithAMobileNumberHasNoEmailToStop() {
        DomRuleViolation refused = assertThrows(DomRuleViolation.class, () -> prefs.set("dp-1", true, false, null).block());
        assertEquals(409, refused.httpStatus());
        assertEquals("email-not-available", refused.problemType());
        assertTrue(stored.isEmpty());

        DpPref pushOnly = prefs.set("dp-1", false, true, null).block();
        assertFalse(pushOnly.pushEnabled());
        assertNull(pushOnly.emailHash());
    }

    @Test
    void closingAnAccountErasesThePreferencesToo() {
        prefs.set("dp-1", true, false, "ann@example.com").block();
        EraseDpHandler erase = new EraseDpHandler(dispatch, devices, repo);

        Map<String, Object> done = erase.handle("e-1", "dp-1", null).block();

        assertEquals(1L, done.get("preferencesDeleted"));
        assertTrue(stored.isEmpty());
        assertEquals(0L, erase.handle("e-1", "dp-1", null).block().get("preferencesDeleted"), "running it again changes nothing");
        notifier.handle(toEmail("ann@example.com")).block();
        assertEquals(1, emails.size(), "once the account is gone the choice is too");
    }

    @Test
    void theAddressIsShownMasked() {
        assertEquals("a•••@example.com", DpPrefCmdHandler.mask("ann@example.com"));
        assertEquals("•••", DpPrefCmdHandler.mask("@example.com"));
        assertNull(DpPrefCmdHandler.mask(null));
        assertNull(DpPrefCmdHandler.mask(" "));
    }
}
