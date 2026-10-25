package dev.seagull.ftbrsbridge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only inventory view used by Minecraft's inventory_changed trigger.
 * It leaves the player's real inventory untouched while exposing network stacks
 * as additional inventory slots for predicate evaluation.
 */
final class VirtualAdvancementInventory extends Inventory {
    private final List<ItemStack> virtualItems;

    VirtualAdvancementInventory(
            ServerPlayer player,
            List<ItemStack> playerItems,
            List<ItemStack> backpackItems,
            List<ItemStack> networkItems
    ) {
        super(player);
        virtualItems = new ArrayList<>(playerItems.size() + backpackItems.size() + networkItems.size());
        playerItems.forEach(stack -> virtualItems.add(stack.copy()));
        backpackItems.forEach(stack -> virtualItems.add(stack.copy()));
        networkItems.forEach(stack -> virtualItems.add(stack.copy()));
    }

    @Override
    public int getContainerSize() {
        return virtualItems.size();
    }

    @Override
    public ItemStack getItem(int slot) {
        return virtualItems.get(slot);
    }
}
