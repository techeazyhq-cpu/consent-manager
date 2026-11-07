package com.techeazyhq.cm.bil.dom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

class UsgAlertTest {

    @Test
    void thresholdsAreReachedAtTheirExactShare() {
        assertEquals(OptionalInt.empty(), UsgAlert.highestReached(799, 1_000));
        assertEquals(OptionalInt.of(80), UsgAlert.highestReached(800, 1_000));
        assertEquals(OptionalInt.of(80), UsgAlert.highestReached(999, 1_000));
        assertEquals(OptionalInt.of(100), UsgAlert.highestReached(1_000, 1_000));
        // past it, when a batch overshoots or the counter is read late
        assertEquals(OptionalInt.of(100), UsgAlert.highestReached(1_250, 1_000));
    }

    @Test
    void onlyTheHighestIsRaisedWhenSeveralAreCrossedAtOnce() {
        // 50% to 100% between two sweeps: the fiduciary is told it is out, not also that it passed 80%
        assertEquals(OptionalInt.of(100), UsgAlert.highestReached(1_000, 1_000));
    }

    @Test
    void aPlanOfNothingOrNoUsageNeverAlerts() {
        assertEquals(OptionalInt.empty(), UsgAlert.highestReached(5, 0));
        assertEquals(OptionalInt.empty(), UsgAlert.highestReached(0, 0));
        assertEquals(OptionalInt.empty(), UsgAlert.highestReached(0, 1_000));
        assertEquals(OptionalInt.empty(), UsgAlert.highestReached(-3, 1_000));
    }

    @Test
    void roundingDoesntSkipTheThresholdOnSmallPlans() {
        // 80% of 7 is 5.6: 6 is over it, 5 is under
        assertEquals(OptionalInt.empty(), UsgAlert.highestReached(5, 7));
        assertEquals(OptionalInt.of(80), UsgAlert.highestReached(6, 7));
    }

    @Test
    void onlyTheFullThresholdSaysItIsUsedUp() {
        Instant at = Instant.parse("2026-11-08T10:00:00Z");
        assertFalse(new UsgAlert("df-1", "2026-11", 80, 800, 1_000, "FREE", at).exhausted());
        assertTrue(new UsgAlert("df-1", "2026-11", 100, 1_000, 1_000, "FREE", at).exhausted());
    }
}
