package dev.seagull.ftbrsbridge;

final class ProgressRules {
    private ProgressRules() {
    }

    static long nextMonotonicProgress(long current, long available, long maximum) {
        long nonNegativeCurrent = Math.max(0L, current);
        long clampedAvailable = Math.max(0L, Math.min(available, maximum));
        return Math.max(nonNegativeCurrent, clampedAvailable);
    }

    static long remaining(long current, long maximum) {
        return Math.max(0L, maximum - Math.max(0L, Math.min(current, maximum)));
    }
}
