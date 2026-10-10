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

import com.techeazyhq.cm.domkit.Agg;

/** A DF's active subscription (plan §11) — id is the {@code dfId} itself (one-to-one). */
public final class DfSub extends Agg<String> {

    private String subPlnId;
    private DfSubSts sts;
    private final Instant curPeriodFrom;
    private Instant curPeriodTo;

    DfSub(String dfId, String subPlnId, DfSubSts sts, Instant curPeriodFrom, Instant curPeriodTo) {
        super(dfId);
        this.subPlnId = subPlnId;
        this.sts = sts;
        this.curPeriodFrom = curPeriodFrom;
        this.curPeriodTo = curPeriodTo;
    }

    public String subPlnId() {
        return subPlnId;
    }

    public DfSubSts sts() {
        return sts;
    }

    public Instant curPeriodFrom() {
        return curPeriodFrom;
    }

    public Instant curPeriodTo() {
        return curPeriodTo;
    }

    public void assignPlan(String newSubPlnId) {
        this.subPlnId = newSubPlnId;
    }

    public void suspend() {
        this.sts = DfSubSts.SUSPENDED;
    }

    public void reactivate() {
        this.sts = DfSubSts.ACTIVE;
    }
}
