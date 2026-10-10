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

package com.techeazyhq.cm.ntf.dom;

/**
 * A single outbound notification (plan §9a) — one per channel a `NotifyDpCmd`/`NotifyDfCmd` fans out to, or an OTP code.
 * {@code ref} is a stable id of what triggered the message (e.g. {@code cm-notify-dp-<cnsReqId>}): adapters that call
 * an external delivery service use it as the idempotency key, so a redelivered command is never sent twice.
 */
public record NtfMsg(String destination, String subject, String body, NtfCtg category, String ref) {

    public NtfMsg {
        if (category == null) {
            category = NtfCtg.TRANSACTIONAL;
        }
    }

    public NtfMsg(String destination, String subject, String body) {
        this(destination, subject, body, NtfCtg.TRANSACTIONAL, null);
    }
}
