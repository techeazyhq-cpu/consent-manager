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

package com.techeazyhq.cm.audkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class AuditTrailTest {

    @Test
    void stampsEachActionWithTheActorOfTheRequest_orTheSystemWithoutOne() {
        List<PlatformActionEvt> recorded = new ArrayList<>();
        AuditTrail trail = AuditTrail.to("bil", evt -> Mono.fromRunnable(() -> recorded.add(evt)));
        Actor admin = new Actor(Actor.Kind.STAFF, "s-1", "admin@cm.local");

        trail.record("plan-change.approved", "df-1", "req-1", AuditTrail.details("from", "FREE", "to", "STANDARD"))
                .contextWrite(ctx -> Actors.with(ctx, admin)).block();
        trail.record("subscription.started", "df-1", "df-1").block();

        assertEquals(admin, recorded.get(0).actor());
        assertEquals("bil", recorded.get(0).ctx());
        assertEquals(Map.of("from", "FREE", "to", "STANDARD"), recorded.get(0).details());
        assertEquals(Actor.SYSTEM, recorded.get(1).actor());
    }

    @Test
    void anActionIsNounDotVerb_andDetailsSkipNulls() {
        assertThrows(IllegalArgumentException.class,
                () -> PlatformActionEvt.of("bil", "Approved", null, null, Actor.SYSTEM, null));
        assertEquals(Map.of("to", "STANDARD"), AuditTrail.details("from", null, "to", "STANDARD"));
        assertEquals(true, PlatformActionEvt.isAction("member.invited"));
        assertEquals(false, PlatformActionEvt.isAction("CnsGrantedEvt"));
    }

    @Test
    void namesFallBackToTheId() {
        assertEquals("client-1", new Actor(Actor.Kind.DF_SYSTEM, "client-1", " ").name());
    }
}
