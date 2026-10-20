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

package com.techeazyhq.cm.cnscmd.infra;

import com.techeazyhq.cm.cnscmd.dom.SecretSealerPort;
import com.techeazyhq.cm.seckit.AesGcmSealer;

/** {@link SecretSealerPort} with the platform's AES-GCM sealer, keyed by {@code cm.cnscmd.secrets-key} (ADR-047). */
public class AesGcmSecretSealer implements SecretSealerPort {

    private final AesGcmSealer sealer;

    public AesGcmSecretSealer(String base64Key) {
        this.sealer = new AesGcmSealer(base64Key, "cm.cnscmd.secrets-key");
    }

    @Override
    public String seal(String secret) {
        return sealer.seal(secret);
    }

    @Override
    public String open(String sealed) {
        return sealer.open(sealed);
    }
}
