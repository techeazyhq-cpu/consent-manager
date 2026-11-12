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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.cnscmd.dom.AiDraftingAccess.View;

/** Who may draft with the platform's model, and how a request for it runs (ADR-114). */
class AiDraftingAccessTest {

    private static final Instant T = Instant.parse("2026-11-12T10:00:00Z");

    @Test
    void aFiduciaryStartsWithoutIt() {
        assertEquals(View.NOT_ENABLED, AiDraftingAccess.none("df-1").view());
    }

    @Test
    void askingWaitsAndOnlyOneRequestCanWait() {
        var asked = AiDraftingAccess.none("df-1").ask("u1", "  we write many purposes ", T);
        assertEquals(View.PENDING, asked.view());
        assertEquals("we write many purposes", asked.request().note());
        assertThrows(IllegalStateException.class, () -> asked.ask("u2", null, T));
    }

    @Test
    void approvingTurnsItOnAndClosesTheRequest() {
        var on = AiDraftingAccess.none("df-1").ask("u1", null, T).grant(T);
        assertEquals(View.ENABLED, on.view());
        assertEquals(AiDraftingAccess.RequestSts.NONE, on.request().sts());
        assertThrows(IllegalStateException.class, () -> on.ask("u1", null, T), "no need to ask for what it has");
    }

    @Test
    void decliningNeedsAReasonTheFiduciaryCanSeeAndItMayAskAgain() {
        var asked = AiDraftingAccess.none("df-1").ask("u1", "please", T);
        assertThrows(IllegalArgumentException.class, () -> asked.decline("a1", "  ", T));
        var declined = asked.decline("a1", "Not for trial accounts", T);
        assertEquals(View.DECLINED, declined.view());
        assertEquals("Not for trial accounts", declined.request().declineReason());
        assertEquals("a1", declined.request().decidedBy());
        assertEquals(View.PENDING, declined.ask("u1", "now a paid plan", T).view());
    }

    @Test
    void withdrawingNeedsAWaitingRequest() {
        var asked = AiDraftingAccess.none("df-1").ask("u1", null, T);
        assertEquals(View.NOT_ENABLED, asked.withdraw(T).view());
        assertThrows(IllegalStateException.class, () -> AiDraftingAccess.none("df-1").withdraw(T));
        assertThrows(IllegalStateException.class, () -> AiDraftingAccess.none("df-1").decline("a", "no", T));
    }

    @Test
    void revokingTurnsItOffAndItCanBeAskedForAgain() {
        var off = AiDraftingAccess.none("df-1").grant(T).revoke(T);
        assertEquals(View.NOT_ENABLED, off.view());
        assertEquals(View.PENDING, off.ask("u1", null, T).view());
    }

    @Test
    void aLongNoteIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AiDraftingAccess.none("df-1").ask("u", "x".repeat(AiDraftingAccess.MAX_NOTE + 1), T));
    }
}
