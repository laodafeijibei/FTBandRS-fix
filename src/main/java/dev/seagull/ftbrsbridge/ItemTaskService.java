package dev.seagull.ftbrsbridge;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.StackListEntry;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.item.MissingItem;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ItemTaskService {
    private static final long FULL_SCAN_INTERVAL_TICKS = 1200L;

    private final Map<UUID, TaskScanState> scanStates = new HashMap<>();
    private final Set<UUID> dirtyPlayers = new HashSet<>();

    void scan(
            ServerPlayer player,
            CarriedInventorySnapshot carried,
            NetworkItemSnapshot networkSnapshot,
            long gameTime
    ) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) {
            return;
        }
        TeamData teamData = TeamData.get(player);
        if (teamData.isLocked()) {
            return;
        }
        String fingerprint = carried.fingerprint() + "|network:" + networkSnapshot.fingerprint();
        if (shouldSkip(player.getUUID(), fingerprint, gameTime)) {
            return;
        }
        AvailableItemIndex availableItems = AvailableItemIndex.create(
                carried.taskItems(),
                networkSnapshot
        );
        file.withPlayerContext(
                player,
                () -> scanWithPlayerContext(file, teamData, player, availableItems)
        );
        dirtyPlayers.remove(player.getUUID());
        scanStates.put(player.getUUID(), new TaskScanState(fingerprint, gameTime));
    }

    void markDirty(UUID playerId) {
        dirtyPlayers.add(playerId);
    }

    void markDirty(Iterable<UUID> playerIds) {
        for (UUID playerId : playerIds) {
            dirtyPlayers.add(playerId);
        }
    }

    void markAllDirty() {
        dirtyPlayers.addAll(scanStates.keySet());
    }

    private boolean shouldSkip(UUID playerId, String fingerprint, long gameTime) {
        TaskScanState previous = scanStates.get(playerId);
        if (previous == null) {
            return false;
        }
        if (dirtyPlayers.remove(playerId)) {
            return false;
        }
        return previous.fingerprint().equals(fingerprint)
                && gameTime - previous.gameTime() < FULL_SCAN_INTERVAL_TICKS;
    }

    private void scanWithPlayerContext(
            ServerQuestFile file,
            TeamData teamData,
            ServerPlayer player,
            AvailableItemIndex availableItems
    ) {
        for (Task rawTask : file.getAllTasks()) {
            if (!(rawTask instanceof ItemTask task) || !canEvaluate(teamData, task)) {
                continue;
            }
            if (!TaskSubmissionPolicy.evaluateDuringPassiveScan(
                    task.consumesResources(),
                    task.isTaskScreenOnly(),
                    task.getQuest().canBeRepeated()
            )) {
                continue;
            }
            try {
                long maximum = task.getMaxProgress();
                long matchingItems = availableItems.countMatching(
                        task.getItemStack(),
                        task::test,
                        ItemMatchingSystem.INSTANCE.isItemFilter(task.getItemStack()),
                        maximum
                );
                updateDetectionProgress(teamData, player, task, matchingItems);
            } catch (RuntimeException exception) {
                Diagnostics.warnThrottled(
                        "task-" + task.getCodeString(),
                        "Failed to evaluate FTB item task {} for player {}",
                        task.getCodeString(),
                        player.getGameProfile().getName(),
                        exception
                );
            }
        }
    }

    void submitManually(
            TeamData teamData,
            ServerPlayer player,
            CarriedInventorySnapshot carried,
            List<AccessibleNetwork> networks,
            ItemTask task
    ) {
        if (!TaskSubmissionPolicy.useExtendedSourceForManualSubmit(
                task.consumesResources(),
                task.isTaskScreenOnly()
        ) || !canEvaluate(teamData, task)) {
            return;
        }
        consumeIfComplete(teamData, player, carried, networks, task);
    }

    private boolean canEvaluate(TeamData teamData, ItemTask task) {
        if (task.isTaskScreenOnly()
                || teamData.isCompleted(task)
                || task.getItemStack().isEmpty()
                || task.getItemStack().getItem() instanceof MissingItem
                || !teamData.canStartTasks(task.getQuest())) {
            return false;
        }
        return isSequenceAvailable(teamData, task);
    }

    private boolean isSequenceAvailable(TeamData teamData, ItemTask task) {
        Quest quest = task.getQuest();
        if (!quest.getRequireSequentialTasks()) {
            return true;
        }
        List<Task> tasks = quest.getTasksAsList();
        int index = tasks.indexOf(task);
        return index == 0 || index > 0 && teamData.isCompleted(tasks.get(index - 1));
    }

    private void updateDetectionProgress(
            TeamData teamData,
            ServerPlayer player,
            ItemTask task,
            long available
    ) {
        long maximum = task.getMaxProgress();
        long current = teamData.getProgress(task);
        long next = ProgressRules.nextMonotonicProgress(current, available, maximum);
        if (next > current) {
            Diagnostics.debug(
                    "Advancing task {} for {} from {} to {}",
                    task.getCodeString(),
                    player.getGameProfile().getName(),
                    current,
                    next
            );
            teamData.setProgress(task, next);
            markDirty(player.getUUID());
        }
    }

    void clearPlayer(UUID playerId) {
        scanStates.remove(playerId);
        dirtyPlayers.remove(playerId);
    }

    private void consumeIfComplete(
            TeamData teamData,
            ServerPlayer player,
            CarriedInventorySnapshot carried,
            List<AccessibleNetwork> networks,
            ItemTask task
    ) {
        long maximum = task.getMaxProgress();
        long remaining = ProgressRules.remaining(teamData.getProgress(task), maximum);
        if (remaining == 0L) {
            teamData.setProgress(task, maximum);
            return;
        }

        ConsumptionPlan plan = createConsumptionPlan(player, carried, networks, task, remaining);
        if (plan == null) {
            return;
        }
        if (!executeConsumptionPlan(player, plan)) {
            return;
        }
        teamData.setProgress(task, maximum);
        markDirty(player.getUUID());
        Diagnostics.debug(
                "Consumed {} items and completed task {} for {}",
                remaining,
                task.getCodeString(),
                player.getGameProfile().getName()
        );
    }

    private ConsumptionPlan createConsumptionPlan(
            ServerPlayer player,
            CarriedInventorySnapshot carried,
            List<AccessibleNetwork> networks,
            ItemTask task,
            long required
    ) {
        long remaining = required;
        List<PlayerRemoval> playerRemovals = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().items.size() && remaining > 0L; slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (stack.isEmpty() || !task.test(stack)) {
                continue;
            }
            int amount = (int) Math.min(remaining, stack.getCount());
            playerRemovals.add(new PlayerRemoval(slot, stack.copy(), amount));
            remaining -= amount;
        }

        List<HandlerRemoval> backpackRemovals = new ArrayList<>();
        for (BackpackAccess backpack : carried.backpacks()) {
            remaining = planHandler(
                    backpack.inventory(),
                    task,
                    remaining,
                    backpack.location() + ".inventory",
                    backpackRemovals
            );
            if (remaining == 0L) {
                break;
            }
        }

        List<NetworkRemoval> networkRemovals = new ArrayList<>();
        for (AccessibleNetwork accessible : networks) {
            if (remaining == 0L) {
                break;
            }
            for (ItemStack stack : sortedNetworkStacks(accessible.network())) {
                if (remaining == 0L) {
                    break;
                }
                if (stack.isEmpty() || !task.test(stack)) {
                    continue;
                }
                int requested = (int) Math.min(remaining, Math.min(stack.getCount(), Integer.MAX_VALUE));
                ItemStack simulated = accessible.network().extractItem(stack, requested, Action.SIMULATE);
                int available = Math.min(requested, simulated.getCount());
                if (available <= 0) {
                    continue;
                }
                networkRemovals.add(new NetworkRemoval(accessible.network(), stack.copy(), available));
                remaining -= available;
            }
        }

        List<HandlerRemoval> upgradeRemovals = new ArrayList<>();
        if (remaining > 0L) {
            for (BackpackAccess backpack : carried.backpacks()) {
                remaining = planHandler(
                        backpack.upgrades(),
                        task,
                        remaining,
                        backpack.location() + ".upgrades",
                        upgradeRemovals
                );
                if (remaining == 0L) {
                    break;
                }
            }
        }
        return remaining == 0L
                ? new ConsumptionPlan(playerRemovals, backpackRemovals, networkRemovals, upgradeRemovals)
                : null;
    }

    private long planHandler(
            IItemHandler handler,
            ItemTask task,
            long remaining,
            String source,
            List<HandlerRemoval> removals
    ) {
        for (int slot = 0; slot < handler.getSlots() && remaining > 0L; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !task.test(stack)) {
                continue;
            }
            int requested = (int) Math.min(remaining, stack.getCount());
            ItemStack simulated = handler.extractItem(slot, requested, true);
            int available = Math.min(requested, simulated.getCount());
            if (available <= 0) {
                continue;
            }
            removals.add(new HandlerRemoval(handler, slot, stack.copy(), available, source));
            remaining -= available;
        }
        return remaining;
    }

    private List<ItemStack> sortedNetworkStacks(INetwork network) {
        return network.getItemStorageCache().getList().getStacks().stream()
                .map(StackListEntry::getStack)
                .filter(stack -> !stack.isEmpty())
                .sorted(Comparator.comparing(this::stackSortKey))
                .toList();
    }

    private String stackSortKey(ItemStack stack) {
        String itemId = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        return itemId + '|' + (stack.hasTag() ? stack.getTag() : "");
    }

    private boolean executeConsumptionPlan(ServerPlayer player, ConsumptionPlan plan) {
        List<AppliedHandlerRemoval> appliedHandlers = new ArrayList<>();
        List<CommittedExtraction> committed = new ArrayList<>();
        try {
            for (PlayerRemoval removal : plan.playerRemovals()) {
                ItemStack current = player.getInventory().items.get(removal.slot());
                if (current.getCount() < removal.amount() || !ItemStack.isSameItemSameTags(current, removal.original())) {
                    throw new IllegalStateException("Player inventory changed during consumption commit");
                }
                current.shrink(removal.amount());
            }
            for (HandlerRemoval removal : plan.allHandlerRemovals()) {
                ItemStack extracted = removal.handler().extractItem(removal.slot(), removal.amount(), false);
                if (extracted.getCount() != removal.amount()) {
                    throw new IllegalStateException("Backpack extraction returned fewer items at " + removal.source());
                }
                appliedHandlers.add(new AppliedHandlerRemoval(removal, extracted.copy()));
            }
            for (NetworkRemoval removal : plan.networkRemovals()) {
                ItemStack extracted = removal.network().extractItem(
                        removal.template(),
                        removal.amount(),
                        Action.PERFORM
                );
                if (!extracted.isEmpty()) {
                    committed.add(new CommittedExtraction(removal.network(), extracted.copy()));
                }
                if (extracted.getCount() != removal.amount()) {
                    throw new IllegalStateException("RS extraction returned fewer items than simulated");
                }
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return true;
        } catch (RuntimeException exception) {
            rollback(player, plan, appliedHandlers, committed);
            Diagnostics.warnThrottled(
                    "consume-exception",
                    "Failed while committing an extended FTB item-task consumption",
                    exception
            );
            return false;
        }
    }

    private void rollback(
            ServerPlayer player,
            ConsumptionPlan plan,
            List<AppliedHandlerRemoval> appliedHandlers,
            List<CommittedExtraction> committed
    ) {
        for (CommittedExtraction extraction : committed) {
            returnToPlayerOrDrop(
                    player,
                    extraction.network().insertItem(
                            extraction.stack().copy(),
                            extraction.stack().getCount(),
                            Action.PERFORM
                    ),
                    "RS rollback"
            );
        }
        for (int index = appliedHandlers.size() - 1; index >= 0; index--) {
            AppliedHandlerRemoval applied = appliedHandlers.get(index);
            restoreHandlerSlot(player, applied.removal(), applied.removal().original());
        }
        for (PlayerRemoval removal : plan.playerRemovals()) {
            player.getInventory().items.set(removal.slot(), removal.original().copy());
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private void restoreHandlerSlot(ServerPlayer player, HandlerRemoval removal, ItemStack original) {
        if (removal.handler() instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(removal.slot(), original.copy());
            return;
        }
        ItemStack remainder = removal.handler().insertItem(removal.slot(), original.copy(), false);
        if (!remainder.isEmpty()) {
            returnToPlayerOrDrop(player, remainder, "backpack rollback");
            Diagnostics.warnThrottled(
                    "backpack-rollback-" + removal.source(),
                    "Backpack rollback could not restore slot {} at {}",
                    removal.slot(),
                    removal.source()
            );
        }
    }

    private void returnToPlayerOrDrop(ServerPlayer player, ItemStack stack, String reason) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remainder = stack.copy();
        if (!player.getInventory().add(remainder) && !remainder.isEmpty()) {
            player.drop(remainder, false);
        }
        Diagnostics.warnThrottled(
                "consumption-rollback-remainder",
                "{} left {} item(s) outside the original source; returned to player or dropped",
                reason,
                stack.getCount()
        );
    }

    private record TaskScanState(String fingerprint, long gameTime) {
    }

    private record PlayerRemoval(int slot, ItemStack original, int amount) {
    }

    private record HandlerRemoval(
            IItemHandler handler,
            int slot,
            ItemStack original,
            int amount,
            String source
    ) {
    }

    private record AppliedHandlerRemoval(HandlerRemoval removal, ItemStack extracted) {
    }

    private record NetworkRemoval(INetwork network, ItemStack template, int amount) {
    }

    private record CommittedExtraction(INetwork network, ItemStack stack) {
    }

    private record ConsumptionPlan(
            List<PlayerRemoval> playerRemovals,
            List<HandlerRemoval> backpackRemovals,
            List<NetworkRemoval> networkRemovals,
            List<HandlerRemoval> upgradeRemovals
    ) {
        List<HandlerRemoval> allHandlerRemovals() {
            List<HandlerRemoval> all = new ArrayList<>(backpackRemovals.size() + upgradeRemovals.size());
            all.addAll(backpackRemovals);
            all.addAll(upgradeRemovals);
            return all;
        }
    }
}
