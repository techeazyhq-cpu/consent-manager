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

package com.techeazyhq.cm.dfreg.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dfreg.dom.EmailPort;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Mono;

/** {@link EmailPort}: a command to cm-ntf-svc's {@code cmd-notify-email} topic (ADR-060), which sends the mail. */
public class PulsarEmailAdapter implements EmailPort {

    private final PulsarDfEvtProducer producer;
    private final ObjectMapper objectMapper;

    public PulsarEmailAdapter(PulsarDfEvtProducer producer, ObjectMapper objectMapper) {
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> send(String destination, String subject, String body, String ref) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(new NotifyEmailCmdPayload(destination, subject,
                        body, ref)))
                .flatMap(json -> producer.publish(PulsarTopics.cmd("ntf", "notify-email"), json));
    }

    private record NotifyEmailCmdPayload(String destination, String subject, String body, String ref) {
    }
}
