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

package com.techeazyhq.cm.idn.otp;

import java.time.Duration;

import org.keycloak.models.SingleUseObjectProvider;

/**
 * {@link OtpAbuseGuard.Slots} in Keycloak's single-use object cache (ADR-061): shared by every node, entries expire by
 * themselves, and {@code putIfAbsent} is atomic, so two nodes can't both take the same slot.
 */
final class SingleUseSlots implements OtpAbuseGuard.Slots {

    private final SingleUseObjectProvider store;

    SingleUseSlots(SingleUseObjectProvider store) {
        this.store = store;
    }

    @Override
    public boolean claim(String key, Duration ttl) {
        return store.putIfAbsent(key, Math.max(1, ttl.toSeconds()));
    }

    @Override
    public boolean taken(String key) {
        return store.contains(key);
    }

    @Override
    public void release(String key) {
        store.remove(key);
    }
}
