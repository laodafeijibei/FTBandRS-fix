package dev.seagull.ftbrsbridge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable per-scan view of the player's real inventory and carried backpacks. */
final class CarriedInventorySnapshot {
    private final List<ItemStack> playerItems;
    private final List<ItemStack> playerInventorySlots;
    private final List<ItemStack> backpackItems;
    private final List<BackpackAccess> backpacks;
    private final String fingerprint;
    private final boolean backpackReadFailure;
    private final boolean sawBackpack;

    private CarriedInventorySnapshot(
            List<ItemStack> playerItems,
            List<ItemStack> playerInventorySlots,
            List<ItemStack> backpackItems,
            List<BackpackAccess> backpacks,
            String fingerprint,
            boolean backpackReadFailure,
            boolean sawBackpack
    ) {
        this.playerItems = List.copyOf(playerItems);
        this.playerInventorySlots = List.copyOf(playerInventorySlots);
        this.backpackItems = List.copyOf(backpackItems);
        this.backpacks = List.copyOf(backpacks);
        this.fingerprint = fingerprint;
        this.backpackReadFailure = backpackReadFailure;
        this.sawBackpack = sawBackpack;
    }

    static CarriedInventorySnapshot capture(ServerPlayer player, SophisticatedBackpackCompat compat) {
        List<ItemStack> playerItems = new ArrayList<>(player.getInventory().items.size());
        player.getInventory().items.forEach(stack -> playerItems.add(stack.copy()));
        List<ItemStack> playerInventorySlots = new ArrayList<>(player.getInventory().getContainerSize());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            playerInventorySlots.add(player.getInventory().getItem(slot).copy());
        }

        BackpackDiscovery discovery = compat.find(player);
        List<BackpackAccess> validBackpacks = new ArrayList<>();
        List<ItemStack> backpackItems = new ArrayList<>();
        boolean readFailure = discovery.readFailure();
        for (BackpackAccess backpack : discovery.backpacks()) {
            try {
                backpackItems.addAll(backpack.readMainItems());
                backpackItems.addAll(backpack.readUpgradeItems());
                validBackpacks.add(backpack);
            } catch (RuntimeException exception) {
                readFailure = true;
                Diagnostics.warnThrottled(
                        "sophisticated-backpack-content-" + backpack.location(),
                        "Could not read contents of Sophisticated Backpack at {}",
                        backpack.location(),
                        exception
                );
            }
        }
        String fingerprint = fingerprint(playerInventorySlots, backpackItems, readFailure);
        return new CarriedInventorySnapshot(
                playerItems,
                playerInventorySlots,
                backpackItems,
                validBackpacks,
                fingerprint,
                readFailure,
                discovery.sawBackpack()
        );
    }

    List<ItemStack> playerItems() {
        return playerItems;
    }

    List<ItemStack> playerInventorySlots() {
        return playerInventorySlots;
    }

    List<ItemStack> backpackItems() {
        return backpackItems;
    }

    List<ItemStack> taskItems() {
        List<ItemStack> items = new ArrayList<>(playerItems.size() + backpackItems.size());
        items.addAll(playerItems);
        items.addAll(backpackItems);
        return items;
    }

    List<BackpackAccess> backpacks() {
        return backpacks;
    }

    String fingerprint() {
        return fingerprint;
    }

    boolean hasBackpackSource() {
        return sawBackpack || !backpacks.isEmpty();
    }

    boolean hasBackpackReadFailure() {
        return backpackReadFailure;
    }

    private static String fingerprint(List<ItemStack> playerSlots, List<ItemStack> backpackItems, boolean failure) {
        List<String> entries = new ArrayList<>(playerSlots.size() + backpackItems.size() + 1);
        for (ItemStack stack : playerSlots) {
            entries.add("player:" + stackKey(stack));
        }
        for (ItemStack stack : backpackItems) {
            entries.add("backpack:" + stackKey(stack));
        }
        if (failure) {
            entries.add("backpack-read-failure");
        }
        Collections.sort(entries);
        return String.join(";", entries);
    }

    private static String stackKey(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()))
                + "#" + stack.getCount()
                + "#" + (stack.hasTag() ? stack.getTag() : "");
    }
}
