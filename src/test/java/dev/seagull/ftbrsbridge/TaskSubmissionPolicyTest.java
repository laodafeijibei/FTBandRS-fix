package dev.seagull.ftbrsbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskSubmissionPolicyTest {
    @Test
    void nonConsumingTasksRemainPassiveDetectionTasks() {
        assertTrue(TaskSubmissionPolicy.evaluateDuringPassiveScan(false, false, false));
        assertFalse(TaskSubmissionPolicy.useExtendedSourceForManualSubmit(false, false));
    }

    @Test
    void repeatableNonConsumingTasksRemainNativeOnly() {
        assertFalse(TaskSubmissionPolicy.evaluateDuringPassiveScan(false, false, true));
        assertFalse(TaskSubmissionPolicy.useExtendedSourceForManualSubmit(false, false));
    }

    @Test
    void consumingTasksOnlyUseExtendedSourcesAfterManualSubmit() {
        assertFalse(TaskSubmissionPolicy.evaluateDuringPassiveScan(true, false, false));
        assertTrue(TaskSubmissionPolicy.useExtendedSourceForManualSubmit(true, false));
    }

    @Test
    void taskScreenOnlyTasksAreNeverBridged() {
        assertFalse(TaskSubmissionPolicy.evaluateDuringPassiveScan(false, true, false));
        assertFalse(TaskSubmissionPolicy.evaluateDuringPassiveScan(true, true, false));
        assertFalse(TaskSubmissionPolicy.useExtendedSourceForManualSubmit(false, true));
        assertFalse(TaskSubmissionPolicy.useExtendedSourceForManualSubmit(true, true));
    }
}
