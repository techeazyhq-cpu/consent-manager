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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.techeazyhq.cm.cnscmd.dom.WhkSenderPort;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

/**
 * Posts webhooks (ADR-047) with a 10-second timeout, never following redirects, and only to public addresses: a URL
 * whose host resolves to loopback, link-local (cloud metadata), private, carrier-grade NAT, multicast or unique-local
 * addresses is refused, at registration and again before every send, since DNS can change in between. Local
 * development may allow private addresses ({@code cm.webhooks.allow-private}); production never does.
 */
public class WebClientWhkSender implements WhkSenderPort {

    static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final boolean allowPrivate;

    public WebClientWhkSender(WebClient.Builder builder, boolean allowPrivate) {
        this.webClient = builder.clone()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)
                        .responseTimeout(TIMEOUT)))
                .build();
        this.allowPrivate = allowPrivate;
    }

    @Override
    public Mono<Result> post(String url, Map<String, String> headers, String body) {
        return refusal(url).flatMap(refused -> refused.isPresent()
                ? Mono.just(new Result(null, "refused: " + refused.get()))
                : webClient.post()
                        .uri(URI.create(url))
                        .headers(h -> headers.forEach(h::set))
                        .bodyValue(body)
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(new Result(response.statusCode().value(), null)))
                        .timeout(TIMEOUT.plusSeconds(1))
                        .onErrorResume(e -> Mono.just(new Result(null, describe(e)))));
    }

    @Override
    public Mono<Optional<String>> refusal(String url) {
        return Mono.fromCallable(() -> {
            String host = URI.create(url).getHost();
            if (host == null) {
                return Optional.of("the URL has no host");
            }
            InetAddress[] addresses;
            try {
                addresses = InetAddress.getAllByName(host);
            } catch (UnknownHostException e) {
                return Optional.of("the host " + host + " doesn't resolve");
            }
            if (allowPrivate) {
                return Optional.<String>empty();
            }
            for (InetAddress a : addresses) {
                if (isInternal(a)) {
                    return Optional.of("the host " + host + " is on a private or local network");
                }
            }
            return Optional.<String>empty();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    static boolean isInternal(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            // 100.64.0.0/10 (carrier-grade NAT), 0.0.0.0/8, 192.0.0.0/24 (IETF), 198.18.0.0/15 (benchmarking)
            return (first == 100 && second >= 64 && second <= 127) || first == 0
                    || (first == 192 && second == 0 && (b[2] & 0xff) == 0) || (first == 198 && (second == 18 || second == 19));
        }
        // fc00::/7 unique local, and an IPv4-mapped address judged as IPv4
        if (a instanceof Inet6Address) {
            if ((b[0] & 0xfe) == 0xfc) {
                return true;
            }
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= b[i] == 0;
            }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isInternal(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String describe(Throwable e) {
        Throwable cause = e instanceof WebClientRequestException && e.getCause() != null ? e.getCause() : e;
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
