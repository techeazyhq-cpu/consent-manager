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

package com.techeazyhq.cm.evtkit;

import java.util.regex.Pattern;

/**
 * Topic-naming convention (plan §13): {@code persistent://<tenant>/<ctx>/<kind>-<name-kebab>-v1}
 * — tenant {@code cm}, namespace = bounded-context abbreviation, kind {@code evt}|{@code cmd}.
 *
 * <p>The sandbox (ADR-037) shares the broker but not a single topic: its services run with
 * {@code CM_PULSAR_TENANT=cm-sbx}, read once when the process starts, so no sandbox event can reach a production
 * consumer, and the reverse.
 */
public final class PulsarTopics {

    public static final String DEFAULT_TENANT = "cm";
    private static final Pattern TENANT_NAME = Pattern.compile("^[a-z][a-z0-9-]{0,62}$");
    private static final String TENANT = tenant(System.getenv("CM_PULSAR_TENANT"));

    private PulsarTopics() {
    }

    public static String evt(String ctx, String eventNameKebab) {
        return topic(ctx, "evt", eventNameKebab);
    }

    public static String cmd(String ctx, String commandNameKebab) {
        return topic(ctx, "cmd", commandNameKebab);
    }

    /** Regex pattern matching every event topic in a context's namespace, for Key_Shared projector subscriptions. */
    public static String evtPattern(String ctx) {
        return "persistent://" + TENANT + "/" + ctx + "/evt-.*";
    }

    /** The tenant every topic of this process lives in. */
    public static String tenant() {
        return TENANT;
    }

    /** {@code configured}, or {@code cm} when unset; a name Pulsar can't hold stops the process from starting. */
    static String tenant(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_TENANT;
        }
        String name = configured.trim();
        if (!TENANT_NAME.matcher(name).matches()) {
            throw new IllegalStateException("CM_PULSAR_TENANT is not a valid Pulsar tenant name: " + name);
        }
        return name;
    }

    public static String subscription(String ctx, String purpose) {
        return ctx + "-" + purpose + "-sub";
    }

    private static String topic(String ctx, String kind, String nameKebab) {
        return "persistent://" + TENANT + "/" + ctx + "/" + kind + "-" + nameKebab + "-v1";
    }
}
