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

package com.techeazyhq.cm.ntf.dom;

import reactor.core.publisher.Mono;

/**
 * Tells the consent lifecycle that a data principal could not be told about a consent request: the email or SMS was
 * refused for good (a bad address, a channel not allowed). cm-cns-cmd-svc then marks the request
 * {@code NOTIFICATION_FAILED}, so the fiduciary and platform staff see it instead of a request that looks pending.
 */
public interface DpDeliveryOutcomePort {

    Mono<Void> deliveryFailed(String cnsReqId, NtfChnl channel, String code);
}
