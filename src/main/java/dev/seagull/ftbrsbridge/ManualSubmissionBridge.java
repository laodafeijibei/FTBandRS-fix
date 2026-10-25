package dev.seagull.ftbrsbridge;

import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ManualSubmissionBridge {
    private static final CarriedTerminalScanner TERMINAL_SCANNER = new CarriedTerminalScanner();
    private static final NetworkAccessResolver NETWORK_RESOLVER = new NetworkAccessResolver();
    private static final SophisticatedBackpackCompat BACKPACK_COMPAT = new SophisticatedBackpackCompat();
    private static final ItemTaskService ITEM_TASK_SERVICE = new ItemTaskService();

    private ManualSubmissionBridge() {
    }

    static void resetBackpackLifecycle() {
        BACKPACK_COMPAT.resetForServerLifecycle();
    }

    public static boolean tryHandle(Task rawTask, TeamData teamData, ServerPlayer player) {
        if (!(rawTask instanceof ItemTask task)
                || !TaskSubmissionPolicy.useExtendedSourceForManualSubmit(
                task.consumesResources(),
                task.isTaskScreenOnly()
        )) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        CarriedInventorySnapshot carried = CarriedInventorySnapshot.capture(player, BACKPACK_COMPAT);
        List<AccessibleNetwork> networks = List.of();
        try {
            List<ItemStack> terminals = TERMINAL_SCANNER.find(player);
            if (!terminals.isEmpty()) {
                networks = NETWORK_RESOLVER.resolve(server, player, terminals);
            }
        } catch (RuntimeException exception) {
            Diagnostics.warnThrottled(
                    "manual-submit-network-" + player.getUUID(),
                    "Could not resolve an RS network for a manual FTB task submission by {}",
                    player.getGameProfile().getName(),
                    exception
            );
        }

        boolean hasExtendedSource = carried.hasBackpackSource() || !networks.isEmpty();
        if (!hasExtendedSource) {
            return false;
        }
        if (carried.hasBackpackReadFailure()) {
            Diagnostics.warnThrottled(
                    "manual-submit-backpack-" + player.getUUID(),
                    "Manual FTB item-task submission aborted because a carried backpack could not be read"
            );
            return true;
        }

        try {
            ITEM_TASK_SERVICE.submitManually(teamData, player, carried, networks, task);
        } catch (RuntimeException exception) {
            Diagnostics.warnThrottled(
                    "manual-submit-task-" + task.getCodeString(),
                    "Manual extended submission failed for FTB item task {} and player {}",
                    task.getCodeString(),
                    player.getGameProfile().getName(),
                    exception
            );
        }

        // An extended source was part of this click, so native submission must
        // not partially consume the player's inventory after our transaction.
        return true;
    }
}
