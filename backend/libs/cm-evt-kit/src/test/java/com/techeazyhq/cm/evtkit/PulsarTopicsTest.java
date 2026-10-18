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

package com.techeazyhq.cm.evtkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PulsarTopicsTest {

    @Test
    void namesTopicsByContextKindAndVersionInTheProcessTenant() {
        String tenant = PulsarTopics.tenant();
        assertEquals("persistent://" + tenant + "/cnscmd/evt-cns-granted-v1", PulsarTopics.evt("cnscmd", "cns-granted"));
        assertEquals("persistent://" + tenant + "/ntf/cmd-notify-dp-v1", PulsarTopics.cmd("ntf", "notify-dp"));
        assertEquals("persistent://" + tenant + "/cnscmd/evt-.*", PulsarTopics.evtPattern("cnscmd"));
    }

    @Test
    void production_isTenantCm_andTheSandboxNamesItsOwn() {
        assertEquals("cm", PulsarTopics.tenant(null));
        assertEquals("cm", PulsarTopics.tenant(" "));
        assertEquals("cm-sbx", PulsarTopics.tenant("cm-sbx"));
    }

    @Test
    void refusesATenantPulsarCouldNotHold() {
        assertThrows(IllegalStateException.class, () -> PulsarTopics.tenant("cm/sbx"));
        assertThrows(IllegalStateException.class, () -> PulsarTopics.tenant("CM"));
    }
}
