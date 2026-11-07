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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.ntf.dom.NtfCtg;
import com.techeazyhq.cm.ntf.dom.NtfDeliveryRejectedException;
import com.techeazyhq.cm.ntf.dom.NtfMsg;

import reactor.core.publisher.Mono;

class NotifyEmailCmdHandlerTest {

    private final List<NtfMsg> emails = new ArrayList<>();
    private final NotifyEmailCmdHandler handler = new NotifyEmailCmdHandler(new NtfDispatchService(
            msg -> Mono.fromRunnable(() -> emails.add(msg)),
            msg -> Mono.error(new AssertionError("no SMS")),
            msg -> Mono.error(new AssertionError("no WhatsApp")),
            msg -> Mono.error(new AssertionError("no push"))));

    @Test
    void sendsTheEmailAsTransactionalMailWithItsReference() {
        handler.handle(new NotifyEmailCmd(" owner@df.example ", "Usage", "80% used", "cm-usage-df-1")).block();

        assertEquals(1, emails.size());
        assertEquals("owner@df.example", emails.get(0).destination());
        assertEquals(NtfCtg.TRANSACTIONAL, emails.get(0).category());
        assertEquals("cm-usage-df-1", emails.get(0).ref());
    }

    @Test
    void anEmailMissingItsPartsIsRefusedForGoodNotRetried() {
        assertThrows(NtfDeliveryRejectedException.class,
                () -> handler.handle(new NotifyEmailCmd(" ", "Usage", "body", "r")).block());
        assertThrows(NtfDeliveryRejectedException.class,
                () -> handler.handle(new NotifyEmailCmd("a@b.example", null, "body", "r")).block());
        assertEquals(0, emails.size());
    }
}
