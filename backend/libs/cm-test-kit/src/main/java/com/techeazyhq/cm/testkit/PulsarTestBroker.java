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

package com.techeazyhq.cm.testkit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A standalone Pulsar for integration tests, the image the local stack runs (infra/common/compose.yml), with the
 * {@code cm} tenant and the namespaces a test names created the way {@code infra/pulsar/init-tenant.sh} does. Kept to
 * a small heap and without functions or the stream storage, like the local broker (ADR-052).
 */
public final class PulsarTestBroker {

    private PulsarTestBroker() {
    }

    public static PulsarContainer container() {
        return new PulsarContainer(DockerImageName.parse("apachepulsar/pulsar:3.3.3"))
                .withEnv("PULSAR_MEM", "-Xms256m -Xmx512m -XX:MaxDirectMemorySize=256m");
    }

    /** Creates tenant {@code cm} and {@code cm/<namespace>} for each one given; already there counts as done. */
    public static void createNamespaces(PulsarContainer pulsar, String... namespaces) {
        String admin = pulsar.getHttpServiceUrl() + "/admin/v2/";
        put(admin + "tenants/cm", "{\"allowedClusters\":[\"standalone\"]}");
        for (String namespace : namespaces) {
            put(admin + "namespaces/cm/" + namespace, "");
        }
    }

    private static void put(String url, String body) {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2 && response.statusCode() != 409) {
                throw new IllegalStateException("PUT " + url + " -> " + response.statusCode() + " " + response.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException("PUT " + url + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted creating Pulsar namespaces", e);
        }
    }
}
