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

import java.time.Instant;

/**
 * Whether one fiduciary may draft purposes with the platform's model (ADR-114). Off until platform staff turn it on, at onboarding or when the
 * fiduciary asks. A fiduciary has at most one request waiting: the request is part of this record, so two cannot exist. The history (who asked, who
 * decided, why) is in the audit trail; this keeps only the latest request.
 */
public record AiDraftingAccess(String dfId, boolean enabled, Request request, Instant updatedAt) {

    public enum RequestSts {
        NONE, PENDING, DECLINED
    }

    /** The latest request. {@code declineReason} is set when declined and shown to the fiduciary. */
    public record Request(RequestSts sts, String note, String requestedBy, Instant requestedAt, String decidedBy, Instant decidedAt, String declineReason) {

        static final Request NONE = new Request(RequestSts.NONE, null, null, null, null, null, null);
    }

    public static final int MAX_NOTE = 500;

    /** What a person sees: enabled, waiting, turned down, or not enabled. */
    public enum View {
        ENABLED, PENDING, DECLINED, NOT_ENABLED
    }

    public static AiDraftingAccess none(String dfId) {
        return new AiDraftingAccess(dfId, false, Request.NONE, null);
    }

    public View view() {
        return enabled ? View.ENABLED : request.sts() == RequestSts.PENDING ? View.PENDING : request.sts() == RequestSts.DECLINED ? View.DECLINED : View.NOT_ENABLED;
    }

    /** The fiduciary asks. Not while it already has it or already has a request waiting; a declined one may be asked again. */
    public AiDraftingAccess ask(String by, String note, Instant now) {
        if (enabled) {
            throw new IllegalStateException("Drafting with a model is already on for this fiduciary");
        }
        if (request.sts() == RequestSts.PENDING) {
            throw new IllegalStateException("A request is already waiting for an answer");
        }
        String n = note == null ? null : note.strip();
        if (n != null && n.length() > MAX_NOTE) {
            throw new IllegalArgumentException("Keep the note to " + MAX_NOTE + " characters");
        }
        return new AiDraftingAccess(dfId, false, new Request(RequestSts.PENDING, n == null || n.isEmpty() ? null : n, by, now, null, null, null), now);
    }

    /** The fiduciary takes back a request that is still waiting. */
    public AiDraftingAccess withdraw(Instant now) {
        if (request.sts() != RequestSts.PENDING) {
            throw new IllegalStateException("There is no request waiting");
        }
        return new AiDraftingAccess(dfId, enabled, Request.NONE, now);
    }

    /** Staff turn it on: at onboarding, directly, or by approving a request (which then is closed). */
    public AiDraftingAccess grant(Instant now) {
        return new AiDraftingAccess(dfId, true, Request.NONE, now);
    }

    /** Staff turn it off; it can be asked for again. */
    public AiDraftingAccess revoke(Instant now) {
        return new AiDraftingAccess(dfId, false, Request.NONE, now);
    }

    /** Staff turn down a waiting request, saying why. */
    public AiDraftingAccess decline(String by, String reason, Instant now) {
        if (request.sts() != RequestSts.PENDING) {
            throw new IllegalStateException("There is no request waiting");
        }
        String r = reason == null ? "" : reason.strip();
        if (r.isEmpty() || r.length() > MAX_NOTE) {
            throw new IllegalArgumentException("Say why, in at most " + MAX_NOTE + " characters");
        }
        Request q = request;
        return new AiDraftingAccess(dfId, false, new Request(RequestSts.DECLINED, q.note(), q.requestedBy(), q.requestedAt(), by, now, r), now);
    }
}
