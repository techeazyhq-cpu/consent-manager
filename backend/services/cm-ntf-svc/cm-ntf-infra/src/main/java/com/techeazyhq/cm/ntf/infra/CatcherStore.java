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

package com.techeazyhq.cm.ntf.infra;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.techeazyhq.cm.ntf.dom.NtfMsg;

/**
 * Our own minimal message-catcher store (plan §9a) — the SMS/WhatsApp equivalent of what
 * Mailpit does for email. Nothing ubiquitous/trustworthy exists in the OSS ecosystem for
 * SMS/WhatsApp the way Mailpit does for SMTP, so rather than depend on an obscure
 * single-maintainer Docker image (or a paid gateway, deferred to Phase 2), this platform
 * builds its own: capture-in-memory, inspect via a small GET endpoint. In-memory and capped
 * — this is a dev/test tool, never wired into a production profile.
 */
public class CatcherStore {

    private static final int MAX_CAPTURED = 500;

    private final ConcurrentLinkedDeque<Captured> captured = new ConcurrentLinkedDeque<>();

    public void capture(NtfMsg msg) {
        captured.addFirst(new Captured(Instant.now(), msg.destination(), msg.subject(), msg.body()));
        while (captured.size() > MAX_CAPTURED) {
            captured.removeLast();
        }
    }

    public List<Captured> list() {
        return List.copyOf(captured);
    }

    public record Captured(Instant capturedAt, String destination, String subject, String body) {
    }
}
