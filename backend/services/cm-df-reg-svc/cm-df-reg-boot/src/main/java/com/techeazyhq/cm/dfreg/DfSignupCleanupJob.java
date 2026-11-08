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


package com.techeazyhq.cm.dfreg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.techeazyhq.cm.dfreg.app.DfSignupHandler;

/** Deletes applications nobody confirmed, a week after their link expired (ADR-065): they hold an address no one proved. */
public class DfSignupCleanupJob {

    private static final Logger LOG = LoggerFactory.getLogger(DfSignupCleanupJob.class);

    private final DfSignupHandler signups;

    public DfSignupCleanupJob(DfSignupHandler signups) {
        this.signups = signups;
    }

    @Scheduled(fixedDelayString = "${cm.dfreg.signup.cleanup-delay-ms:3600000}", initialDelayString = "${cm.dfreg.signup.cleanup-initial-delay-ms:300000}")
    public void purge() {
        signups.purgeStale().subscribe(n -> {
            if (n > 0) {
                LOG.info("Deleted {} unconfirmed sign-up applications", n);
            }
        }, e -> LOG.warn("Deleting stale sign-up applications failed", e));
    }
}
