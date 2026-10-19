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
import java.util.UUID;

/** Factory Method (plan §12): a {@link CnsReq} can never be constructed in an invalid state. */
public final class CnsReqFactory {

    private CnsReqFactory() {
    }

    public static CnsReq create(String dfId, DpRef dpRef, Prps prps, Scp scp, String ntcTitle, String ntcText) {
        if (dfId == null || dfId.isBlank()) {
            throw new IllegalArgumentException("dfId must not be blank");
        }
        if (ntcTitle == null || ntcTitle.isBlank()) {
            throw new IllegalArgumentException("notice title must not be blank");
        }
        checkInput(prps, scp, ntcTitle, ntcText);
        checkRecipient(dpRef);
        String id = UUID.randomUUID().toString();
        CnsReq cnsReq = new CnsReq(id, dfId, dpRef, prps, scp, ntcTitle, ntcText, CnsReqSts.PENDING, Instant.now());
        cnsReq.markCreated();
        return cnsReq;
    }

    /** The most data categories one request may ask for: a consent covers what a notice can fairly explain. */
    public static final int MAX_DCATS = 50;
    /** A purpose or data category code, as stored (varchar 64). */
    public static final int MAX_CODE = 64;
    public static final int MAX_NTC_TITLE = 255;
    public static final int MAX_NTC_TEXT = 20_000;

    /** A contact address or number, as stored (varchar 255). */
    public static final int MAX_DP_REF = 255;

    /**
     * What a new request must keep to, checked here rather than in the value objects so stored requests always
     * rehydrate: sizes the store and a notice can hold, and no control characters but a notice's line breaks and tabs.
     * Without these, an oversized or NUL-carrying request reached the database as a 500 (VAPT INP-07, INP-08, ZAP).
     */
    static void checkInput(Prps prps, Scp scp, String ntcTitle, String ntcText) {
        if (prps.code().length() > MAX_CODE) {
            throw new IllegalArgumentException("purpose code must be at most " + MAX_CODE + " characters");
        }
        if (scp.dCats().size() > MAX_DCATS) {
            throw new IllegalArgumentException("a request may ask for at most " + MAX_DCATS + " data categories");
        }
        if (scp.dCats().stream().anyMatch(d -> d.code().length() > MAX_CODE)) {
            throw new IllegalArgumentException("data category codes must be at most " + MAX_CODE + " characters");
        }
        if (ntcTitle.length() > MAX_NTC_TITLE) {
            throw new IllegalArgumentException("notice title must be at most " + MAX_NTC_TITLE + " characters");
        }
        if (ntcText != null && ntcText.length() > MAX_NTC_TEXT) {
            throw new IllegalArgumentException("notice text must be at most " + MAX_NTC_TEXT + " characters");
        }
        boolean controlInCodes = hasControl(prps.code(), false)
                || scp.dCats().stream().anyMatch(d -> hasControl(d.code(), false));
        if (controlInCodes || hasControl(ntcTitle, false) || (ntcText != null && hasControl(ntcText, true))) {
            throw new IllegalArgumentException("control characters aren't allowed in a request");
        }
    }

    private static void checkRecipient(DpRef dpRef) {
        if (dpRef instanceof DpRef.UnlnkDpRef unlinked
                && (unlinked.val().length() > MAX_DP_REF || hasControl(unlinked.val(), false))) {
            throw new IllegalArgumentException("the contact must be at most " + MAX_DP_REF
                    + " characters, without control characters");
        }
    }

    private static boolean hasControl(String s, boolean lineBreaksAndTabs) {
        return s.chars().anyMatch(c -> Character.isISOControl(c)
                && !(lineBreaksAndTabs && (c == '\n' || c == '\r' || c == '\t')));
    }

    /** Rehydration from persistence — no invariant re-checking, no event raised. */
    public static CnsReq rehydrate(String id, String dfId, DpRef dpRef, Prps prps, Scp scp, String ntcTitle,
                                    String ntcText, CnsReqSts sts, Instant createdAt) {
        return new CnsReq(id, dfId, dpRef, prps, scp, ntcTitle, ntcText, sts, createdAt);
    }
}
