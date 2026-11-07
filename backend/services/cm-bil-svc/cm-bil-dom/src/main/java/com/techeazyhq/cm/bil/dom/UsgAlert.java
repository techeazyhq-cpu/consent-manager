package com.techeazyhq.cm.bil.dom;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

/**
 * A fiduciary's usage reaching a share of its monthly allowance (ADR-060): once per fiduciary, month and threshold,
 * however many times usage is read afterwards. When one sweep finds usage past several thresholds at once, only the
 * highest is raised: a fiduciary that jumped from 50% to 100% is told it is out, not also that it passed 80%.
 */
public record UsgAlert(String dfId, String period, int threshold, long used, long quota, String planName,
                       Instant raisedAt) {

    /** The shares of the allowance that raise an alert, in percent, ascending. */
    public static final List<Integer> THRESHOLDS = List.of(80, 100);

    /** The highest threshold that {@code used} of {@code quota} has reached, if any. A plan of nothing never alerts. */
    public static OptionalInt highestReached(long used, long quota) {
        if (quota <= 0 || used <= 0) {
            return OptionalInt.empty();
        }
        for (int i = THRESHOLDS.size() - 1; i >= 0; i--) {
            int threshold = THRESHOLDS.get(i);
            if (used * 100 >= quota * threshold) {
                return OptionalInt.of(threshold);
            }
        }
        return OptionalInt.empty();
    }

    /** Whether this is the one that says the allowance is used up. */
    public boolean exhausted() {
        return threshold >= 100;
    }
}
