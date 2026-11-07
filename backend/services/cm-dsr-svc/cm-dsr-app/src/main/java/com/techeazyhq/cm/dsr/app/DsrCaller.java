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

package com.techeazyhq.cm.dsr.app;

/**
 * Who is acting, as their token says: a data principal ({@code dpId}), someone at a fiduciary or one of its systems
 * ({@code dfId}; {@code mayAnswer} when their role lets them answer requests), or platform staff. {@code name} is what
 * a principal reads as who answered: an email address, or the subject when there is none. {@code token} is the
 * caller's own bearer token, used only to ask the Consent context whose consent a request concerns.
 */
public record DsrCaller(String subject, String name, String dpId, String dfId, boolean mayAnswer, boolean staff,
                        String token) {

    public boolean isDp() {
        return dpId != null;
    }

    public boolean isDf() {
        return dfId != null;
    }
}
