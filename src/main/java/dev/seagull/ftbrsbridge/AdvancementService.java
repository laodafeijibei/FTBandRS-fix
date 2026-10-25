package dev.seagull.ftbrsbridge;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bridges network-held items into vanilla inventory_changed advancement criteria. */
final class AdvancementService {
    private final Map<UUID, AdvancementScanState> lastStates = new HashMap<>();

    void scan(
            ServerPlayer player,
            CarriedInventorySnapshot carried,
            NetworkItemSnapshot networkSnapshot
    ) {
        UUID playerId = player.getUUID();
        AdvancementScanState current = AdvancementScanState.capture(
                carried.playerInventorySlots(),
                carried.backpackItems(),
                networkSnapshot
        );
        List<ItemStack> triggerStacks = current.triggerStacksSince(lastStates.put(playerId, current));
        if (triggerStacks.isEmpty()) {
            return;
        }
        VirtualAdvancementInventory virtualInventory =
                new VirtualAdvancementInventory(
                        player,
                        carried.playerInventorySlots(),
                        current.backpackItems(),
                        current.networkItems()
                );
        for (ItemStack triggerStack : triggerStacks) {
            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, virtualInventory, triggerStack);
        }
    }

    void clearPlayer(UUID playerId) {
        lastStates.remove(playerId);
    }
}
