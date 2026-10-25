package dev.seagull.ftbrsbridge;

import dev.architectury.event.EventResult;
import dev.ftb.mods.ftbquests.events.ClearFileCacheEvent;
import dev.ftb.mods.ftbquests.events.ObjectCompletedEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public final class QuestNetworkBridge {
    private final CarriedTerminalScanner terminalScanner = new CarriedTerminalScanner();
    private final NetworkAccessResolver networkResolver = new NetworkAccessResolver();
    private final SophisticatedBackpackCompat backpackCompat = new SophisticatedBackpackCompat();
    private final ItemTaskService itemTaskService = new ItemTaskService();
    private final AdvancementService advancementService = new AdvancementService();
    private int ticksUntilScan;

    public QuestNetworkBridge() {
        ObjectCompletedEvent.TASK.register(event -> {
            itemTaskService.markDirty(event.getOnlineMembers().stream().map(ServerPlayer::getUUID).toList());
            return EventResult.pass();
        });
        ObjectCompletedEvent.QUEST.register(event -> {
            itemTaskService.markDirty(event.getOnlineMembers().stream().map(ServerPlayer::getUUID).toList());
            return EventResult.pass();
        });
        ClearFileCacheEvent.EVENT.register(file -> itemTaskService.markAllDirty());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        backpackCompat.resetForServerLifecycle();
        ManualSubmissionBridge.resetBackpackLifecycle();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        backpackCompat.resetForServerLifecycle();
        ManualSubmissionBridge.resetBackpackLifecycle();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ticksUntilScan > 0) {
            ticksUntilScan--;
            return;
        }
        ticksUntilScan = BridgeConfig.SCAN_INTERVAL_TICKS.get() - 1;
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            scanPlayer(server, player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            advancementService.clearPlayer(player.getUUID());
            itemTaskService.clearPlayer(player.getUUID());
        }
    }

    private void scanPlayer(MinecraftServer server, ServerPlayer player) {
        long startedAt = System.nanoTime();
        long backpackStarted = startedAt;
        long backpackElapsed;
        long networkElapsed = 0L;
        long advancementElapsed = 0L;
        long taskElapsed = 0L;
        try {
            CarriedInventorySnapshot carried = CarriedInventorySnapshot.capture(player, backpackCompat);
            backpackElapsed = elapsedMillis(backpackStarted);

            long networkStarted = System.nanoTime();
            List<AccessibleNetwork> networks = List.of();
            List<ItemStack> terminals = terminalScanner.find(player);
            if (!terminals.isEmpty()) {
                networks = networkResolver.resolve(server, player, terminals);
            }
            NetworkItemSnapshot networkSnapshot = NetworkItemSnapshot.capture(networks);
            networkElapsed = elapsedMillis(networkStarted);

            if (!carried.hasBackpackSource() && networks.isEmpty()) {
                advancementService.clearPlayer(player.getUUID());
                itemTaskService.clearPlayer(player.getUUID());
                return;
            }

            long advancementStarted = System.nanoTime();
            try {
                advancementService.scan(player, carried, networkSnapshot);
            } catch (RuntimeException exception) {
                Diagnostics.warnThrottled(
                        "advancement-" + player.getUUID(),
                        "Vanilla advancement scan failed for player {}",
                        player.getGameProfile().getName(),
                        exception
                );
            }
            advancementElapsed = elapsedMillis(advancementStarted);

            long taskStarted = System.nanoTime();
            try {
                itemTaskService.scan(player, carried, networkSnapshot, player.serverLevel().getGameTime());
            } catch (RuntimeException exception) {
                Diagnostics.warnThrottled(
                        "task-scan-" + player.getUUID(),
                        "FTB task scan failed for player {}",
                        player.getGameProfile().getName(),
                        exception
                );
            }
            taskElapsed = elapsedMillis(taskStarted);
            long elapsedMillis = elapsedMillis(startedAt);
            if (elapsedMillis > 50L) {
                Diagnostics.warnThrottled(
                        "slow-scan-" + player.getUUID(),
                        "FTB RS Bridge scan for {} took {} ms (backpack={} ms, network={} ms, FTB={} ms, advancement={} ms)",
                        player.getGameProfile().getName(),
                        elapsedMillis,
                        backpackElapsed,
                        networkElapsed,
                        taskElapsed,
                        advancementElapsed
                );
            } else {
                Diagnostics.debug(
                        "Scan for {} took {} ms (backpack={} ms, network={} ms, FTB={} ms, advancement={} ms)",
                        player.getGameProfile().getName(),
                        elapsedMillis,
                        backpackElapsed,
                        networkElapsed,
                        taskElapsed,
                        advancementElapsed
                );
            }
        } catch (RuntimeException exception) {
            Diagnostics.warnThrottled(
                    "player-" + player.getUUID(),
                    "FTB RS Bridge scan failed for player {}",
                    player.getGameProfile().getName(),
                    exception
            );
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
