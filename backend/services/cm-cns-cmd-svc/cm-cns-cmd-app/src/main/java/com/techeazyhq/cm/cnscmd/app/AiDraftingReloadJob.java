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

package com.techeazyhq.cm.cnscmd.app;

import org.springframework.scheduling.annotation.Scheduled;

/** Reads the drafting-model setting again every so often, so a change staff made through one instance reaches the others (ADR-113). */
public class AiDraftingReloadJob {

    private final AiDraftingSettings settings;

    public AiDraftingReloadJob(AiDraftingSettings settings) {
        this.settings = settings;
    }

    @Scheduled(fixedDelayString = "${cm.cnscmd.ai-drafting.reload-ms:30000}", initialDelayString = "${cm.cnscmd.ai-drafting.reload-ms:30000}")
    public void reload() {
        settings.reload().subscribe(v -> {
        }, err -> org.slf4j.LoggerFactory.getLogger(AiDraftingReloadJob.class).warn("Drafting model setting reload failed", err));
    }
}
