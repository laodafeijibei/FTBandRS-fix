package dev.seagull.ftbrsbridge;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(FtbRsBridge.MOD_ID)
public final class FtbRsBridge {
    public static final String MOD_ID = "ftbrsbridge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FtbRsBridge() {
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                BridgeConfig.SPEC,
                "ftbrsbridge-server.toml"
        );
        MinecraftForge.EVENT_BUS.register(new QuestNetworkBridge());
    }
}
