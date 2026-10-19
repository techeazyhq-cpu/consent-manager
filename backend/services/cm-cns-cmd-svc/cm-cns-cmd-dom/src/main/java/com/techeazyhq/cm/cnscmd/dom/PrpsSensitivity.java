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

package com.techeazyhq.cm.cnscmd.dom;

/**
 * How sensitive a data category is (ADR-031, compliance architecture §6.2), least to most. Raising a category's
 * sensitivity in a new purpose version is a material change: the principal agreed to less.
 */
public enum PrpsSensitivity {
    ORDINARY,
    SENSITIVE,
    PHI,
    CHILD;

    public boolean isAbove(PrpsSensitivity other) {
        return ordinal() > other.ordinal();
    }
}
