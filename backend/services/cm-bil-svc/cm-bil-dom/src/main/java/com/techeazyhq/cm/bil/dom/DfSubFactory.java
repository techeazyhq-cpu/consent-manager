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

package com.techeazyhq.cm.bil.dom;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class DfSubFactory {

    private DfSubFactory() {
    }

    /** Auto-created on {@code DfActivatedEvt} (plan §7c, §11), on the plan chosen at onboarding (FREE by default). */
    public static DfSub create(String dfId, String subPlnId) {
        Instant now = Instant.now();
        return new DfSub(dfId, subPlnId, DfSubSts.ACTIVE, now, now.plus(30, ChronoUnit.DAYS));
    }

    public static DfSub rehydrate(String dfId, String subPlnId, DfSubSts sts, Instant curPeriodFrom, Instant curPeriodTo) {
        return new DfSub(dfId, subPlnId, sts, curPeriodFrom, curPeriodTo);
    }
}
