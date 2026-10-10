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

/**
 * A delivery the channel refused for good (bad recipient, channel not allowed, out of credit): sending the same
 * message again cannot succeed, so callers report it instead of retrying.
 */
public class NtfDeliveryRejectedException extends RuntimeException {

    private final String code;

    public NtfDeliveryRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
