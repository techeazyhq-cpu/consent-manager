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
import java.util.Set;
import java.util.UUID;

/**
 * One event in a consent record (ISO/IEC TS 27560 Events section): when, what, who, and the consent's scope and
 * validity as the event left them. Appended only, never changed.
 *
 * @param seq     its place in the record, from 1
 * @param dCats   the categories in force after it; null when it doesn't change them or ends the consent
 * @param detail  what a {@link Type#MODIFIED} changed, such as {@code CATEGORY_WITHDRAWN:NAME}; null otherwise
 */
public record CnsRecEvt(int seq, UUID evtId, Instant occurredAt, Type type, Actor actor, Set<String> dCats,
                        Instant validFrom, Instant validUntil, String detail) {

    /** The 27560 event, with the W3C DPV consent status it leaves the consent in. */
    public enum Type {
        REQUESTED("dpv:ConsentRequested"),
        GIVEN("dpv:ConsentGiven"),
        REFUSED("dpv:ConsentRefused"),
        MODIFIED("dpv:ConsentGiven"),
        RENEWED("dpv:RenewedConsentGiven"),
        WITHDRAWN("dpv:ConsentWithdrawn"),
        LAPSED("dpv:ConsentExpired");

        private final String dpvStatus;

        Type(String dpvStatus) {
            this.dpvStatus = dpvStatus;
        }

        public String dpvStatus() {
            return dpvStatus;
        }
    }

    /** Who acted: the fiduciary asks, the principal decides, the platform records a lapse. */
    public enum Actor {
        FIDUCIARY,
        PRINCIPAL,
        PLATFORM
    }

    public CnsRecEvt {
        dCats = dCats == null ? null : Set.copyOf(dCats);
    }

    /** The same event at its place in a record. */
    public CnsRecEvt at(int newSeq) {
        return new CnsRecEvt(newSeq, evtId, occurredAt, type, actor, dCats, validFrom, validUntil, detail);
    }
}
