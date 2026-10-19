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
 * Kinds of processing the law treats specially (ADR-022: never for children, DPDPA s.9(3)). A purpose declares them,
 * so they can be refused where they aren't allowed; adding one is a material change.
 */
public enum PrpsFlag {
    TRACKING,
    BEHAVIOURAL_MONITORING,
    TARGETED_ADVERTISING
}
