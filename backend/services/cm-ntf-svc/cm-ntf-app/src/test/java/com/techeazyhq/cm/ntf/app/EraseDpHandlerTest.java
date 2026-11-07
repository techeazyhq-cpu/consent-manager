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

package com.techeazyhq.cm.ntf.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.ntf.dom.NtfDeliveryRejectedException;
import com.techeazyhq.cm.ntf.dom.NtfMsg;
import com.techeazyhq.cm.ntf.dom.PushDvc;
import com.techeazyhq.cm.ntf.dom.PushDvcRepoPort;
import com.techeazyhq.cm.ntf.dom.PushPltf;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class EraseDpHandlerTest {

    private final List<NtfMsg> emails = new ArrayList<>();
    private final List<PushDvc> devices = new ArrayList<>(List.of(
            new PushDvc("d1", "dp-1", "t1", PushPltf.WEB, Instant.now(), Instant.now()),
            new PushDvc("d2", "dp-1", "t2", PushPltf.ANDROID, Instant.now(), Instant.now()),
            new PushDvc("d3", "dp-2", "t3", PushPltf.WEB, Instant.now(), Instant.now())));

    private final EraseDpHandler handler = new EraseDpHandler(new NtfDispatchService(
            msg -> msg.destination().equals("bounce@example.com")
                    ? Mono.error(new NtfDeliveryRejectedException("INVALID_RECIPIENT", "no such mailbox"))
                    : Mono.fromRunnable(() -> emails.add(msg)),
            msg -> Mono.error(new AssertionError("no SMS")),
            msg -> Mono.error(new AssertionError("no WhatsApp")),
            msg -> Mono.error(new AssertionError("no push: the devices are being forgotten"))),
            new PushDvcRepoPort() {
                @Override
                public Mono<PushDvc> upsert(String dpId, String token, PushPltf platform) {
                    return Mono.error(new UnsupportedOperationException());
                }

                @Override
                public Flux<PushDvc> findByDpId(String dpId) {
                    return Flux.fromIterable(devices.stream().filter(d -> d.dpId().equals(dpId)).toList());
                }

                @Override
                public Mono<Boolean> delete(String dpId, String id) {
                    return Mono.just(devices.removeIf(d -> d.dpId().equals(dpId) && d.id().equals(id)));
                }
            });

    @Test
    void theNoticeGoesOutThenTheirDevicesAreForgotten() {
        Map<String, Object> result = handler.handle("er-1", "dp-1", "p@example.com").block();

        assertEquals(1, emails.size());
        assertEquals("cm-erasure-er-1", emails.get(0).ref());
        assertTrue(emails.get(0).body().contains("seven years"));
        assertEquals(2L, result.get("devicesDeleted"));
        assertEquals(List.of("dp-2"), devices.stream().map(PushDvc::dpId).toList());
        // again, as after a lost answer: nothing left to delete
        assertEquals(0L, handler.handle("er-1", "dp-1", "p@example.com").block().get("devicesDeleted"));
    }

    @Test
    void anAddressThatBouncesDoesntHoldTheErasureUp() {
        Map<String, Object> result = handler.handle("er-2", "dp-1", "bounce@example.com").block();

        assertEquals(false, result.get("noticeSent"));
        assertEquals(2L, result.get("devicesDeleted"));
    }
}
