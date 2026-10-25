package dev.seagull.ftbrsbridge;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/** A live, already-resolved view of one carried Sophisticated Backpack. */
record BackpackAccess(
        ItemStack carrier,
        IItemHandler inventory,
        IItemHandler upgrades,
        String location
) {
    List<ItemStack> readMainItems() {
        return readNonEmpty(inventory);
    }

    List<ItemStack> readUpgradeItems() {
        return readNonEmpty(upgrades);
    }

    private static List<ItemStack> readNonEmpty(IItemHandler handler) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        return items;
    }
}
