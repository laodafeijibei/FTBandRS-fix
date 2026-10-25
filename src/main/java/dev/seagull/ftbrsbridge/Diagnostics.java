package dev.seagull.ftbrsbridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class Diagnostics {
    private static final long WARN_INTERVAL_MS = 60_000L;
    private static final Map<String, Long> LAST_WARNINGS = new ConcurrentHashMap<>();

    private Diagnostics() {
    }

    static void debug(String message, Object... arguments) {
        if (BridgeConfig.DEBUG_LOGGING.get()) {
            FtbRsBridge.LOGGER.info("[debug] " + message, arguments);
        }
    }

    static void warnThrottled(String key, String message, Object... arguments) {
        long now = System.currentTimeMillis();
        Long previous = LAST_WARNINGS.putIfAbsent(key, now);
        if (previous == null || now - previous >= WARN_INTERVAL_MS) {
            LAST_WARNINGS.put(key, now);
            FtbRsBridge.LOGGER.warn(message, arguments);
        }
    }
}
