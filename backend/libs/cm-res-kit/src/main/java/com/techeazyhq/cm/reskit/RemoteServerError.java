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

package com.techeazyhq.cm.reskit;

/**
 * The remote answered, but with a server-side failure that the same request may get past later (5xx, 429). For an
 * adapter that reads the response itself instead of letting {@code WebClient} raise a
 * {@code WebClientResponseException}; {@link RemoteFailures} counts it against the breaker.
 */
public class RemoteServerError extends RuntimeException {

    public RemoteServerError(String message) {
        super(message);
    }
}
