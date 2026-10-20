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

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.techeazyhq.cm.cnscmd.dom.RcptSignerPort;
import com.techeazyhq.cm.seckit.CnsTknKeys;

/**
 * Signs receipts as compact JWS (RS256, {@code typ: cm-receipt+json}) with the receipt key set (ADR-025): its own keys,
 * never the consent token's, rotated on their own crypto-period, with retired keys kept published.
 */
public class JwsRcptSigner implements RcptSignerPort {

    static final JOSEObjectType TYPE = new JOSEObjectType("cm-receipt+json");

    private final CnsTknKeys keys;
    private final ObjectMapper objectMapper;

    public JwsRcptSigner(CnsTknKeys keys, ObjectMapper objectMapper) {
        this.keys = keys;
        this.objectMapper = objectMapper;
    }

    @Override
    public Signed sign(Map<String, Object> receipt) {
        RSAKey key = keys.signingKey();
        try {
            JWSObject jws = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(TYPE).build(),
                    new Payload(objectMapper.writeValueAsString(receipt)));
            jws.sign(new RSASSASigner(key));
            return new Signed(key.getKeyID(), jws.serialize());
        } catch (JsonProcessingException | JOSEException e) {
            throw new IllegalStateException("Could not sign a receipt", e);
        }
    }

    @Override
    public String publicJwksJson() {
        return keys.publicJwksJson();
    }
}
