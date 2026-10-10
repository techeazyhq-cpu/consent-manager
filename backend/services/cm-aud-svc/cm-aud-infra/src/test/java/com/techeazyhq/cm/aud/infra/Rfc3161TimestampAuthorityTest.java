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

import com.sun.net.httpserver.HttpServer;
import com.techeazyhq.cm.aud.dom.AudAnchor;
import com.techeazyhq.cm.aud.dom.AudChain;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Rfc3161TimestampAuthority} against an in-process RFC 3161 authority built with Bouncy Castle. */
class Rfc3161TimestampAuthorityTest {

    private enum Mode { HONEST, WRONG_HASH, REJECT, HTTP_ERROR }

    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.HONEST);
    private HttpServer server;
    private TimeStampTokenGenerator tokens;
    private Rfc3161TimestampAuthority authority;

    @BeforeEach
    void startAuthority() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        KeyPair keys = rsa.generateKeyPair();
        X500Name name = new X500Name("CN=Test Timestamp Authority");
        X509CertificateHolder cert = new JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                Date.from(Instant.now().minusSeconds(3600)), Date.from(Instant.now().plusSeconds(3600)), name, keys.getPublic())
                .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping))
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate()));
        tokens = new TimeStampTokenGenerator(
                new JcaSimpleSignerInfoGeneratorBuilder().build("SHA256withRSA", keys.getPrivate(),
                        new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(cert)),
                new JcaDigestCalculatorProviderBuilder().build().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1)),
                new ASN1ObjectIdentifier("1.2.3.4.1"));
        tokens.addCertificates(new JcaCertStore(List.of(cert)));

        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/tsa", exchange -> {
            try {
                TimeStampRequest request = new TimeStampRequest(exchange.getRequestBody().readAllBytes());
                byte[] body = switch (mode.get()) {
                    case HONEST -> new TimeStampResponseGenerator(tokens, TSPAlgorithms.ALLOWED)
                            .generate(request, BigInteger.TEN, new Date()).getEncoded();
                    case WRONG_HASH -> new TimeStampResponseGenerator(tokens, TSPAlgorithms.ALLOWED)
                            .generate(new TimeStampRequestGenerator().generate(TSPAlgorithms.SHA256,
                                    HexFormat.of().parseHex(AudChain.sha256Hex("something else")), request.getNonce()),
                                    BigInteger.TEN, new Date()).getEncoded();
                    case REJECT -> new TimeStampResponseGenerator(tokens, TSPAlgorithms.ALLOWED)
                            .generateRejectedResponse(new Exception("policy not supported")).getEncoded();
                    case HTTP_ERROR -> null;
                };
                if (body == null) {
                    exchange.sendResponseHeaders(503, -1);
                } else {
                    exchange.getResponseHeaders().add("Content-Type", "application/timestamp-reply");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
        authority = new Rfc3161TimestampAuthority(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/tsa"), Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void anAuthorityTimestampsTheCheckpointHashAndTheTokenProvesIt() {
        String hash = AudChain.sha256Hex("checkpoint 1");

        AudAnchor anchor = authority.timestamp(1, hash).block();

        assertEquals(1, anchor.checkpointId());
        assertTrue(anchor.authorityUrl().endsWith("/tsa"));
        assertTrue(Duration.between(anchor.timestampedAt(), Instant.now()).abs().getSeconds() < 60);
        assertEquals(hash, authority.verifiedImprint(anchor.token()));
    }

    @Test
    void aTokenWithAnyByteChangedIsRejected() {
        byte[] token = authority.timestamp(1, AudChain.sha256Hex("checkpoint 1")).block().token();
        byte[] tampered = token.clone();
        tampered[tampered.length - 20] ^= 0x01;

        assertThrows(IllegalArgumentException.class, () -> authority.verifiedImprint(tampered));
        assertThrows(IllegalArgumentException.class, () -> authority.verifiedImprint(new byte[] {1, 2, 3}));
    }

    @Test
    void anAnswerForAnotherHashIsRefused() {
        mode.set(Mode.WRONG_HASH);

        assertThrows(Exception.class, () -> authority.timestamp(1, AudChain.sha256Hex("checkpoint 1")).block());
    }

    @Test
    void aRefusalOrAnOutageFailsTheAttempt() {
        mode.set(Mode.REJECT);
        assertThrows(Exception.class, () -> authority.timestamp(1, AudChain.sha256Hex("checkpoint 1")).block());
        mode.set(Mode.HTTP_ERROR);
        assertThrows(Exception.class, () -> authority.timestamp(1, AudChain.sha256Hex("checkpoint 1")).block());
    }
}
