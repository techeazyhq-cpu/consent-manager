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

package com.techeazyhq.cm.ntf.infra;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NtfRequestSignerTest {

    @Test
    void matchesTheReferenceSignatureFromNotificationServicesClientGuide() {
        String signature = NtfRequestSigner.sign("nss_test", 1767225600L, "0123456789abcdef", "POST",
                "/v1/notifications", null, "{\"channel\":\"SMS\"}".getBytes(StandardCharsets.UTF_8));

        assertEquals("2295ae2e9e31c56816963cbcc04632396fa6471d25a8cb667f9003711e053503", signature);
    }
}
