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
 * What a purpose version says about one of the parties it names (ADR-081): who it is as the version spells it, what it does
 * with the data and where. Copied from the fiduciary's register of processors when the version is published and never changed
 * after, so a notice read years later says what it said on the day, whatever the register has become.
 */
public record PrpsParty(String name, String role, String country) {
}
