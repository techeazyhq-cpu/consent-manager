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

import reactor.core.publisher.Mono;

/**
 * Port (plan §9a): implemented by {@code SmsCatcherAdapter} in Phase 1. No trustworthy free
 * OSS SMS gateway/catcher exists the way Mailpit exists for email, so this platform builds
 * its own minimal capture-and-inspect adapter for local dev/testing rather than depending on
 * an obscure, unmaintained third-party Docker image or a paid gateway (Twilio/MSG91/etc.,
 * Phase 2 backlog). Same shape as {@link EmailSenderPort} so both fan out identically from
 * {@code NotifyDpCmd}/{@code NotifyDfCmd} handlers.
 */
public interface SmsSenderPort {

    Mono<Void> send(NtfMsg msg);
}
