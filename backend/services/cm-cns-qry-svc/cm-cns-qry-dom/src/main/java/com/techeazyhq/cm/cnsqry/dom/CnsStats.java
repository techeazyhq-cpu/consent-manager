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

import java.util.Map;

/**
 * How a fiduciary's consent requests stand, for platform staff: every request it has sent, and how many are in each
 * lifecycle status. Counts only; no request or data principal is identified.
 */
public record CnsStats(
        String dfId,
        long sent,
        long pending,
        long granted,
        long denied,
        long revoked,
        long expired,
        long notificationFailed) {

    public static CnsStats of(String dfId, Map<CnsLcySts, Long> countBySts) {
        long pending = count(countBySts, CnsLcySts.PENDING);
        long granted = count(countBySts, CnsLcySts.ISSUED);
        long denied = count(countBySts, CnsLcySts.DENIED);
        long revoked = count(countBySts, CnsLcySts.REVOKED);
        long expired = count(countBySts, CnsLcySts.EXPIRED);
        long notificationFailed = count(countBySts, CnsLcySts.NOTIFICATION_FAILED);
        long sent = countBySts.values().stream().mapToLong(Long::longValue).sum();
        return new CnsStats(dfId, sent, pending, granted, denied, revoked, expired, notificationFailed);
    }

    private static long count(Map<CnsLcySts, Long> countBySts, CnsLcySts sts) {
        return countBySts.getOrDefault(sts, 0L);
    }
}
