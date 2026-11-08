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

/** Port for the record of sign-in codes sent (ADR-080): implemented by {@code R2dbcOtpSendLedgerAdapter} in -infra. */
public interface OtpSendLedgerPort {

    /**
     * How many codes may be sent: to one destination in an hour and in a day, and by one channel in an hour (what the platform is
     * willing to spend). Zero switches a limit off.
     */
    record Limits(int perDestinationPerHour, int perDestinationPerDay, int perChannelPerHour) { }

    /** Takes a place for a code about to be sent, unless a limit is reached (then empty). The result is the place, to give back. */
    Mono<Long> claim(String destinationHash, NtfChnl channel, Limits limits);

    /** Gives a place back: the code was not sent after all. */
    Mono<Void> release(long place);
}
