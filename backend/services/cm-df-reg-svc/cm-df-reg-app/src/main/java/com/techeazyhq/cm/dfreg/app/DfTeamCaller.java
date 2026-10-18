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

package com.techeazyhq.cm.dfreg.app;

import com.techeazyhq.cm.dfreg.dom.DfRole;

/**
 * Who is acting on a fiduciary's team, from their verified token: a member of that fiduciary (with their role), or a
 * member of platform staff. Staff may only invite a fiduciary's Owner, when activation couldn't (ADR-033): running a
 * fiduciary's team is the fiduciary's business, not the consent manager's (ADR-023).
 */
public record DfTeamCaller(String id, String dfId, DfRole role, boolean platformStaff) {

    public static DfTeamCaller member(String id, String dfId, DfRole role) {
        return new DfTeamCaller(id, dfId, role, false);
    }

    public static DfTeamCaller staff(String id) {
        return new DfTeamCaller(id, null, null, true);
    }

    boolean isMemberOf(String dfId) {
        return !platformStaff && role != null && dfId != null && dfId.equals(this.dfId);
    }
}
