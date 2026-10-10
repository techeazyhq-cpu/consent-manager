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

package com.techeazyhq.cm.webkit;

/**
 * Thrown by a Cmd/Qry handler when a domain-level {@code Spec} (Specification pattern)
 * rejects a request — e.g. {@code DfSubEligiblePolicy}, {@code CnsReqDecidablePolicy}.
 * {@link DomProblemDetailHandler} maps this to the {@code problem+json} response the
 * plan specifies for each rule (§7b: {@code subscription-inactive} -> 402,
 * {@code quota-exceeded} -> 429, etc.).
 */
public class DomRuleViolation extends RuntimeException {

    private final int httpStatus;
    private final String problemType;

    public DomRuleViolation(int httpStatus, String problemType, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.problemType = problemType;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String problemType() {
        return problemType;
    }
}
