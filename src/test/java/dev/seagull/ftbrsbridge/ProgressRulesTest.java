package dev.seagull.ftbrsbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressRulesTest {
    @Test
    void detectionProgressNeverFallsWhenAvailableItemsFall() {
        assertEquals(7L, ProgressRules.nextMonotonicProgress(7L, 3L, 10L));
    }

    @Test
    void detectionProgressAdvancesToCurrentAvailability() {
        assertEquals(8L, ProgressRules.nextMonotonicProgress(3L, 8L, 10L));
    }

    @Test
    void detectionProgressCapsNewAvailabilityAtTaskMaximum() {
        assertEquals(10L, ProgressRules.nextMonotonicProgress(3L, 40L, 10L));
    }

    @Test
    void anomalousExistingProgressIsStillNeverReduced() {
        assertEquals(12L, ProgressRules.nextMonotonicProgress(12L, 1L, 10L));
    }

    @Test
    void negativeInputsDoNotCreateNegativeProgress() {
        assertEquals(0L, ProgressRules.nextMonotonicProgress(-2L, -4L, 10L));
    }

    @Test
    void remainingConsumptionUsesOnlyUnfinishedProgress() {
        assertEquals(5L, ProgressRules.remaining(0L, 5L));
        assertEquals(3L, ProgressRules.remaining(2L, 5L));
        assertEquals(0L, ProgressRules.remaining(5L, 5L));
        assertEquals(0L, ProgressRules.remaining(8L, 5L));
        assertEquals(5L, ProgressRules.remaining(-3L, 5L));
    }
}
