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

package com.techeazyhq.cm.dfreg.infra;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.techeazyhq.cm.dfreg.dom.RequestNoncePort;

/** Forgets request nonces whose window has closed (ADR-041); an expired claim never blocks a nonce anyway. */
public class NoncePurgeJob {

    private static final Logger LOG = LoggerFactory.getLogger(NoncePurgeJob.class);

    private final RequestNoncePort nonces;

    public NoncePurgeJob(RequestNoncePort nonces) {
        this.nonces = nonces;
    }

    @Scheduled(fixedDelayString = "${cm.dfreg.nonce-purge.fixed-delay-ms:600000}")
    public void purge() {
        nonces.purgeExpired(Instant.now()).subscribe(n -> {
            if (n > 0) {
                LOG.debug("Purged {} expired request nonces", n);
            }
        }, e -> LOG.warn("Nonce purge failed", e));
    }
}
