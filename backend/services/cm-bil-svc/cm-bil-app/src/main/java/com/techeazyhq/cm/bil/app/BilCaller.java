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

/**
 * Who is acting on billing, as their token says: a person at a fiduciary ({@code dfId}, and whether their role lets
 * them ask for a plan change: Owner or Administrator), or platform staff ({@code admin} for administrators).
 * {@code name} is what records show: their email address, or the subject when there is none.
 */
public record BilCaller(String subject, String name, String dfId, boolean mayRequestPlan, boolean staff, boolean admin) {

    public boolean isDf() {
        return dfId != null;
    }
}
