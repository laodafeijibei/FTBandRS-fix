package dev.seagull.ftbrsbridge;

final class TaskSubmissionPolicy {
    private TaskSubmissionPolicy() {
    }

    static boolean evaluateDuringPassiveScan(
            boolean consumesResources,
            boolean taskScreenOnly,
            boolean repeatable
    ) {
        return !consumesResources && !taskScreenOnly && !repeatable;
    }

    static boolean useExtendedSourceForManualSubmit(boolean consumesResources, boolean taskScreenOnly) {
        return consumesResources && !taskScreenOnly;
    }
}
