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

package com.techeazyhq.cm.dsr.dom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class DpReqTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    private static final Duration DAYS_90 = Duration.ofDays(90);

    private static DpReq relay(DpRgt rgt, String details) {
        return DpReq.relay("dp-1", "df-1", "cns-1", "MARKETING", rgt, details, DAYS_90, NOW);
    }

    @Test
    void theDeadlineIsFixedAtReceipt() {
        DpReq req = relay(DpRgt.ACCESS, null);

        req.acknowledge(NOW.plus(Duration.ofDays(10)));

        assertEquals(NOW.plus(DAYS_90), req.dueBy());
        assertEquals(DpReq.Sts.ACKNOWLEDGED, req.sts());
    }

    @Test
    void accessAndErasureNeedNoMoreThanTheConsent() {
        assertNull(relay(DpRgt.ERASURE, "  ").details());
        assertThrows(IllegalArgumentException.class, () -> relay(DpRgt.CORRECTION, " "));
        assertThrows(IllegalArgumentException.class, () -> relay(DpRgt.GRIEVANCE, null));
    }

    @Test
    void aRelayedRequestIsAboutAConsent() {
        assertThrows(IllegalArgumentException.class,
                () -> DpReq.relay("dp-1", "df-1", null, null, DpRgt.ACCESS, null, DAYS_90, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> DpReq.relay("dp-1", "df-1", "cns-1", null, null, null, DAYS_90, NOW));
    }

    @Test
    void aGrievanceSaysWhatWentWrong() {
        assertThrows(IllegalArgumentException.class, () -> DpReq.grievance("dp-1", "", DAYS_90, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> DpReq.grievance("dp-1", "x".repeat(DpReq.MAX_DETAILS + 1), DAYS_90, NOW));
        DpReq g = DpReq.grievance("dp-1", " The OTP never arrives ", DAYS_90, NOW);
        assertEquals("The OTP never arrives", g.details());
        assertNull(g.dfId());
    }

    @Test
    void aRefusalCitesALegalGround() {
        DpReq req = relay(DpRgt.ERASURE, null);

        assertThrows(IllegalArgumentException.class,
                () -> req.refuse("u", "a@df", "We must keep it", " ", NOW));
        req.refuse("u", "a@df", "We must keep invoices", "Retention required by the GST Act, s.36", NOW);

        assertEquals(DpReq.Sts.REFUSED, req.sts());
        assertEquals("Retention required by the GST Act, s.36", req.refusalGround());
        assertEquals(NOW, req.acknowledgedAt());
    }

    @Test
    void aClosedRequestStaysClosed() {
        DpReq req = relay(DpRgt.ACCESS, null);
        req.resolve("u", "a@df", "Sent to your registered email", NOW);

        assertThrows(IllegalStateException.class, () -> req.withdraw("dp-1", NOW));
        assertThrows(IllegalStateException.class, () -> req.acknowledge(NOW));
        assertFalse(req.sts().open());
    }

    @Test
    void anOpenRequestPastItsDeadlineIsOverdue() {
        DpReq req = relay(DpRgt.ACCESS, null);

        assertFalse(req.overdue(NOW.plus(Duration.ofDays(89))));
        assertTrue(req.overdue(NOW.plus(Duration.ofDays(91))));
        req.withdraw("dp-1", NOW.plus(Duration.ofDays(91)));
        assertFalse(req.overdue(NOW.plus(Duration.ofDays(92))));
    }
}
