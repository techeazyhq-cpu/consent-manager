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

package com.techeazyhq.cm.bil.app;

import java.time.Instant;

import com.techeazyhq.cm.bil.dom.DfSubSts;

/**
 * A fiduciary's subscription as it stands: plan, status and this month's usage against the plan's allowance.
 * {@code usagePeriod} is the calendar month (UTC) usage is counted in; {@code resetsAt} is when the count starts again.
 */
public record DfSubStatusResult(String subPlnId, String subPlnName, DfSubSts dfSubSts, long curPeriodUsg,
                                long reqPerMonthQta, long reqPerSecQta, String usagePeriod, Instant resetsAt) {
}
