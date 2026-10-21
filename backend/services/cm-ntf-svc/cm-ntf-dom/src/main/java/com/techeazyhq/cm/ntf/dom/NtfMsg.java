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

import java.util.Map;

/**
 * A single outbound notification (plan §9a) — one per channel a `NotifyDpCmd`/`NotifyDfCmd` fans out to, or an OTP code.
 * {@code ref} is a stable id of what triggered the message (e.g. {@code cm-notify-dp-<cnsReqId>}): adapters that call
 * an external delivery service use it as the idempotency key, so a redelivered command is never sent twice.
 * {@code data} is a push's key-value pairs for the app that receives it (e.g. {@code cnsReqId}, the request to open
 * when the notification is tapped); never null, empty for every other message.
 */
public record NtfMsg(String destination, String subject, String body, NtfCtg category, String ref,
                     Map<String, String> data) {

    public NtfMsg {
        if (category == null) {
            category = NtfCtg.TRANSACTIONAL;
        }
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public NtfMsg(String destination, String subject, String body, NtfCtg category, String ref) {
        this(destination, subject, body, category, ref, Map.of());
    }

    public NtfMsg(String destination, String subject, String body) {
        this(destination, subject, body, NtfCtg.TRANSACTIONAL, null);
    }
}
