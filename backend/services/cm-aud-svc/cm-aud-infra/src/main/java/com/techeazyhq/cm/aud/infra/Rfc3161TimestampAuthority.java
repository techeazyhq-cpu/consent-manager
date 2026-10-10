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

package com.techeazyhq.cm.aud.infra;

import com.techeazyhq.cm.aud.dom.AudAnchor;
import com.techeazyhq.cm.aud.dom.TimestampAuthorityPort;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Collection;
import java.util.HexFormat;

/**
 * An RFC 3161 timestamp authority over HTTP (ADR-015), with Bouncy Castle. Each request carries the checkpoint hash
 * (SHA-256) and a random nonce and asks for the authority's certificate; the response is accepted only if it grants
 * the request, answers exactly this hash and nonce, and carries a token whose signature verifies against the signing
 * certificate in it, valid at the time it gives and marked for timestamping. The token is stored as returned (DER), so
 * anyone can check it later with {@code openssl ts -verify} and the authority's CA certificate.
 */
public class Rfc3161TimestampAuthority implements TimestampAuthorityPort {

    private static final HexFormat HEX = HexFormat.of();

    private final HttpClient http;
    private final URI url;
    private final Duration timeout;
    private final SecureRandom random = new SecureRandom();

    public Rfc3161TimestampAuthority(HttpClient http, URI url, Duration timeout) {
        this.http = http;
        this.url = url;
        this.timeout = timeout;
    }

    @Override
    public Mono<AudAnchor> timestamp(long checkpointId, String sha256Hex) {
        return Mono.fromCallable(() -> request(checkpointId, sha256Hex)).subscribeOn(Schedulers.boundedElastic());
    }

    private AudAnchor request(long checkpointId, String sha256Hex) throws Exception {
        TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
        generator.setCertReq(true);
        TimeStampRequest request = generator.generate(TSPAlgorithms.SHA256, HEX.parseHex(sha256Hex),
                new BigInteger(64, random));
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(url)
                        .timeout(timeout)
                        .header("Content-Type", "application/timestamp-query")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(request.getEncoded()))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Timestamp authority answered HTTP " + response.statusCode());
        }
        TimeStampResponse answer = new TimeStampResponse(response.body());
        answer.validate(request);
        TimeStampToken token = answer.getTimeStampToken();
        if (token == null) {
            throw new IOException("Timestamp authority refused: " + answer.getStatusString());
        }
        verifySignature(token);
        return new AudAnchor(checkpointId, url.toString(), token.getTimeStampInfo().getGenTime().toInstant(),
                token.getEncoded());
    }

    @Override
    public String verifiedImprint(byte[] tokenDer) {
        try {
            TimeStampToken token = new TimeStampToken(new CMSSignedData(tokenDer));
            if (!NISTObjectIdentifiers.id_sha256.equals(token.getTimeStampInfo().getMessageImprintAlgOID())) {
                throw new IllegalArgumentException("Timestamp token is not over a SHA-256 hash");
            }
            verifySignature(token);
            return HEX.formatHex(token.getTimeStampInfo().getMessageImprintDigest());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Not a valid timestamp token: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifySignature(TimeStampToken token) throws Exception {
        Collection<X509CertificateHolder> signers = token.getCertificates().getMatches(token.getSID());
        if (signers.isEmpty()) {
            throw new IllegalArgumentException("Timestamp token carries no signing certificate");
        }
        token.validate(new JcaSimpleSignerInfoVerifierBuilder().build(signers.iterator().next()));
    }
}
