package dev.seagull.ftbrsbridge;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BridgeConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue SCAN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Server-side scanning settings for FTB RS Bridge.").push("scanning");
        SCAN_INTERVAL_TICKS = builder
                .comment("How often online players are scanned, in server ticks.")
                .defineInRange("scanIntervalTicks", 60, 1, 1200);
        DEBUG_LOGGING = builder
                .comment("Enable rate-limited diagnostic logging.")
                .define("debugLogging", false);
        builder.pop();
        SPEC = builder.build();
    }

    private BridgeConfig() {
    }
}
