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

package com.techeazyhq.cm.cnscmd.dom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Webhooks are signed as Standard Webhooks, retried then dead-lettered, and never carry personal data (ADR-047). */
class WebhookTest {

    private static final Instant T0 = Instant.parse("2026-10-20T00:00:00Z");
    private static final Scp BOTH = new Scp(Set.of(new DCat("EMAIL"), new DCat("NAME")));

    @Test
    void signsAsTheStandardWebhooksSpecification() {
        // the specification's published test vector
        String secret = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
        assertEquals("v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=",
                WhkSignature.header(List.of(secret), "msg_p5jXN8AQM9LWM0D4loKWxJek", 1614265330L,
                        "{\"test\": 2432232314}"));
    }

    @Test
    void signsWithEverySecretInForceDuringARotation() {
        String a = WhkSignature.newSecret();
        String b = WhkSignature.newSecret();
        assertTrue(a.startsWith("whsec_"));
        String[] sigs = WhkSignature.header(List.of(a, b), "m", 1L, "{}").split(" ");
        assertEquals(2, sigs.length);
        assertEquals("v1," + WhkSignature.sign(b, "m", 1L, "{}"), sigs[1]);
        assertThrows(IllegalArgumentException.class, () -> WhkSignature.header(List.of("plain"), "m", 1L, "{}"));
    }

    @Test
    void retriesOnScheduleThenDeadLetters() {
        List<Duration> delays = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5));
        WhkDelivery d = new WhkDelivery("d-1", "e-1", "df-1", "msg-1", "consent.given", "{}", WhkDelivery.Sts.PENDING, 0,
                T0, null, null, null, T0);

        WhkDelivery once = d.after(new WhkDelivery.Attempt(1, T0, 500, null, 12), delays);
        assertEquals(WhkDelivery.Sts.PENDING, once.sts());
        assertEquals(T0.plus(Duration.ofMinutes(1)), once.nextAttemptAt());
        assertEquals(500, once.lastStatus());

        WhkDelivery twice = once.after(new WhkDelivery.Attempt(2, T0, null, "timeout", 10_000), delays);
        assertEquals(T0.plus(Duration.ofMinutes(5)), twice.nextAttemptAt());

        WhkDelivery dead = twice.after(new WhkDelivery.Attempt(3, T0, 404, null, 3), delays);
        assertEquals(WhkDelivery.Sts.FAILED, dead.sts());
        assertNull(dead.nextAttemptAt());

        WhkDelivery replay = dead.replayed(T0.plusSeconds(60));
        assertEquals(WhkDelivery.Sts.PENDING, replay.sts());
        assertEquals("msg-1", replay.msgId(), "a replay keeps the message id, so receivers de-duplicate");
        assertEquals(3, replay.attempts());

        WhkDelivery delivered = replay.after(new WhkDelivery.Attempt(4, T0, 204, null, 5), delays);
        assertEquals(WhkDelivery.Sts.DELIVERED, delivered.sts());
        assertEquals(T0, delivered.deliveredAt());
        assertFalse(new WhkDelivery.Attempt(1, T0, 302, null, 1).succeeded(), "redirects aren't followed");
    }

    @Test
    void endpointsAreHttpsWithoutCredentialsAndKnownTypes() {
        WhkEndpoint.check("https://df.example/hooks", "orders", Set.of("consent.given"), false);
        WhkEndpoint.check("http://localhost:9000/h", null, null, true);
        assertThrows(IllegalArgumentException.class, () -> WhkEndpoint.check("http://df.example/h", null, null, false));
        assertThrows(IllegalArgumentException.class, () -> WhkEndpoint.check("https://u:p@df.example/h", null, null, false));
        assertThrows(IllegalArgumentException.class, () -> WhkEndpoint.check("https://df.example/h#x", null, null, false));
        assertThrows(IllegalArgumentException.class, () -> WhkEndpoint.check("ftp://df.example/h", null, null, true));
        assertThrows(IllegalArgumentException.class, () -> WhkEndpoint.check(" ", null, null, false));
        assertThrows(IllegalArgumentException.class,
                () -> WhkEndpoint.check("https://df.example/h", null, Set.of("consent.eaten"), false));
        assertThrows(IllegalArgumentException.class,
                () -> WhkEndpoint.check("https://df.example/h", "x".repeat(201), null, false));
    }

    @Test
    void aRotatedSecretIsHonouredForADay() {
        WhkEndpoint e = new WhkEndpoint("e-1", "df-1", "https://df.example/h", null, Set.of("consent.given"), true,
                "s1", null, null, T0);
        assertEquals(List.of("s1"), e.sealedSecretsAt(T0));
        WhkEndpoint rotated = e.withSecret("s2", T0);
        assertEquals(List.of("s2", "s1"), rotated.sealedSecretsAt(T0.plus(Duration.ofHours(23))));
        assertEquals(List.of("s2"), rotated.sealedSecretsAt(T0.plus(Duration.ofHours(24))));

        assertTrue(e.wants("consent.given"));
        assertTrue(e.wants(WhkMessage.TEST));
        assertFalse(e.wants("consent.withdrawn"));
        assertFalse(e.withActive(false).wants("consent.given"));
    }

    @Test
    void aRequestBecomesAMessageWithoutTheContactItWasSentTo() {
        CnsReq req = CnsReqFactory.create("df-1", DpRef.unlinked(ExtDpRefTyp.EMAIL, "asha@example.com"),
                CnsReqTemplate.of(new Prps("P"), BOTH, "T", "X", new PrpsVerRef("ver-1", 3, "en", "h")));
        DomainEvtHolder evt = new DomainEvtHolder(req.pullEvts().get(0));

        WhkMessage msg = WhkMessage.of(evt.evt()).orElseThrow();

        assertEquals("consent.requested", msg.type());
        assertEquals("df-1", msg.dfId());
        assertEquals(evt.evt().evtId().toString(), msg.id(), "the event id: the same on every retry");
        assertEquals(req.id(), msg.data().get("cnsReqId"));
        assertEquals(List.of("EMAIL", "NAME"), msg.data().get("dCatCodes"));
        assertEquals(3, msg.data().get("prpsVer"));
        assertFalse(msg.body().toString().contains("asha"), "no personal data in a webhook");
        assertEquals(Set.of("id", "type", "occurredAt", "data"), msg.body().keySet());
    }

    @Test
    void eachChangeOfAConsentHasItsType() {
        CnsArt art = CnsArtFactory.rehydrate("art-1", "req-1", "df-1", DpRef.linked("dp-1"), new Prps("P"), BOTH,
                ValPrd.of(Instant.now(), Duration.ofDays(30)), CnsArtSts.ISSUED, Instant.now(), null);
        art.withdrawCategory(new DCat("NAME"), Instant.now());
        art.revoke();
        List<WhkMessage> msgs = art.pullEvts().stream().map(e -> WhkMessage.of(e).orElseThrow()).toList();

        assertEquals(List.of("consent.modified", "consent.withdrawn"), msgs.stream().map(WhkMessage::type).toList());
        Map<String, Object> modified = msgs.get(0).data();
        assertEquals("NAME", modified.get("dCatWithdrawn"));
        assertEquals(List.of("EMAIL"), modified.get("dCatCodes"));
        assertEquals("dpv:ConsentWithdrawn", msgs.get(1).data().get("status"));
        assertTrue(WhkMessage.TYPES.containsAll(msgs.stream().map(WhkMessage::type).toList()));
    }

    private record DomainEvtHolder(com.techeazyhq.cm.domkit.DomainEvt evt) {
    }
}
