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

package com.techeazyhq.cm.stf.app;

import com.techeazyhq.cm.webkit.DomRuleViolation;

/** Who is asking, from their verified token: their account id and whether they are a platform admin. */
public record StfCaller(String id, boolean admin) {

    void requireAdmin() {
        if (!admin) {
            throw new DomRuleViolation(403, "admin-only", "Only a platform administrator can manage staff");
        }
    }
}
