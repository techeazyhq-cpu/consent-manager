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

package com.techeazyhq.cm.cnsqry.dom;

/**
 * Unified read-side lifecycle status (plan §8) — a read-side simplification distinct from
 * the write side's two aggregate-specific enums ({@code CnsReqSts}/{@code CnsArtSts} in
 * cm-cns-cmd-svc), since the query side presents one lifecycle view regardless of which
 * write aggregate currently owns the record.
 */
public enum CnsLcySts {
    PENDING,
    DENIED,
    ISSUED,
    REVOKED,
    NOTIFICATION_FAILED,
    EXPIRED
}
