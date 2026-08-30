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
