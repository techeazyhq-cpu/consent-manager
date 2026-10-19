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
 * A data category as a purpose version defines it (ADR-031, ADR-026).
 *
 * @param code        the category's code, as tokens and requests name it ({@code EMAIL})
 * @param dpv         its W3C DPV personal data type, such as {@code dpv-pd:EmailAddress}; optional
 * @param sensitivity how sensitive it is
 * @param necessary   whether the fiduciary needs it for the purpose. It never stops the principal refusing or
 *                    withdrawing it (ADR-026 §5); it only means the fiduciary may not go ahead without it.
 * @param consequence what happens if the principal refuses or withdraws it; required when {@code necessary}
 */
public record PrpsDCat(String code, String dpv, PrpsSensitivity sensitivity, boolean necessary, String consequence) {

    public PrpsDCat {
        sensitivity = sensitivity == null ? PrpsSensitivity.ORDINARY : sensitivity;
    }
}
