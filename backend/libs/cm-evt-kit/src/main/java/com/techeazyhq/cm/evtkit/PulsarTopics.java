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

/**
 * Topic-naming convention (plan §13): {@code persistent://cm/<ctx>/<kind>-<name-kebab>-v1}
 * — tenant {@code cm}, namespace = bounded-context abbreviation, kind {@code evt}|{@code cmd}.
 */
public final class PulsarTopics {

    private static final String TENANT = "cm";

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

    public static String subscription(String ctx, String purpose) {
        return ctx + "-" + purpose + "-sub";
    }

    private static String topic(String ctx, String kind, String nameKebab) {
        return "persistent://" + TENANT + "/" + ctx + "/" + kind + "-" + nameKebab + "-v1";
    }
}
