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

package com.techeazyhq.cm.ntf.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

/**
 * DF notification stays a Phase-1 logged placeholder (plan §3) — the user's multi-channel
 * request (plan §7c step 2, §9a) was specifically about notifying the *Data Principal*; a DF
 * doesn't yet have a registered notification contact/channel preference in this slice.
 */
public class NotifyDfCmdHandler {

    private static final Logger LOG = LoggerFactory.getLogger(NotifyDfCmdHandler.class);

    public Mono<Void> handle(NotifyDfCmd cmd) {
        return Mono.fromRunnable(() ->
                LOG.info("NotifyDf: dfId={} subjectId={} outcome={}", cmd.dfId(), cmd.subjectId(), cmd.outcome()));
    }
}
