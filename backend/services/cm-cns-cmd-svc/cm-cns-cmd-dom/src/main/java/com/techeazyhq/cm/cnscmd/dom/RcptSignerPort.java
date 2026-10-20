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

package com.techeazyhq.cm.cnscmd.dom;

/** Signs receipts with the receipt key, kept apart from the consent token key (ADR-025, finding R8). */
public interface RcptSignerPort {

    /** A compact JWS whose payload is {@code receipt} as JSON, and the id of the key that signed it. */
    Signed sign(java.util.Map<String, Object> receipt);

    record Signed(String kid, String jws) {
    }

    /** The receipt keys' public JWKS, retired ones included, so every retained receipt can be verified. */
    String publicJwksJson();
}
