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

import com.techeazyhq.cm.ntf.dom.NtfCtg;
import com.techeazyhq.cm.ntf.dom.NtfMsg;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatcherStoreTest {

    @Test
    void keepsAPushesDataSoALocalRunShowsWhatATapWouldOpen() {
        CatcherStore store = new CatcherStore();
        new PushCatcherAdapter(store).send(new NtfMsg("fcm-token", "New consent request", "Acme asks for consent",
                NtfCtg.TRANSACTIONAL, "cm-notify-dp-req-1-d1", Map.of("cnsReqId", "req-1"))).block();
        store.capture(new NtfMsg("+919800000001", null, "Your code is 123456"));

        assertTrue(store.list().get(0).data().isEmpty());
        assertEquals(Map.of("cnsReqId", "req-1"), store.list().get(1).data());
    }
}
