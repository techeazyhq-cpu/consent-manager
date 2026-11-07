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

/**
 * What a principal asks a fiduciary to do with their data (DPDPA ss.11-14; GDPR Arts. 15-17 for the first three).
 * {@code GRIEVANCE} is a complaint to the fiduciary itself, which the fiduciary must answer before the principal can
 * go to the Board (DPDPA s.13(3)).
 */
public enum DpRgt {
    /** A summary of the data processed under this consent, and with whom it was shared (s.11). */
    ACCESS,
    /** Correct, complete or update the data (s.12). */
    CORRECTION,
    /** Erase the data, as the purpose no longer needs it or the consent ended (s.12). */
    ERASURE,
    /** A complaint to the fiduciary about how it handled the data (s.13). */
    GRIEVANCE,
    /** Name someone to act for the principal on death or incapacity (s.14). */
    NOMINATION;

    /** A correction, a complaint or a nomination says what it is; access and erasure need no more than the consent. */
    public boolean needsDetails() {
        return this == CORRECTION || this == GRIEVANCE || this == NOMINATION;
    }
}
