package dev.seagull.ftbrsbridge;

import com.refinedmods.refinedstorage.api.util.StackListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable per-scan view of all accessible RS item caches. */
final class NetworkItemSnapshot {
    private final List<NetworkStack> stacks;
    private final List<ItemStack> itemStacks;

    NetworkItemSnapshot(List<NetworkStack> stacks) {
        this.stacks = List.copyOf(stacks);
        this.itemStacks = this.stacks.stream().map(NetworkStack::stack).toList();
    }

    static NetworkItemSnapshot capture(List<AccessibleNetwork> networks) {
        List<NetworkStack> stacks = new ArrayList<>();
        for (AccessibleNetwork accessible : networks) {
            for (StackListEntry<ItemStack> entry : accessible.network()
                    .getItemStorageCache().getList().getStacks()) {
                ItemStack stack = entry.getStack();
                if (!stack.isEmpty()) {
                    stacks.add(new NetworkStack(accessible.key(), stack.copy()));
                }
            }
        }
        return new NetworkItemSnapshot(stacks);
    }

    List<NetworkStack> stacks() {
        return stacks;
    }

    List<ItemStack> itemStacks() {
        return itemStacks;
    }

    String fingerprint() {
        List<String> entries = new ArrayList<>(stacks.size());
        for (NetworkStack observed : stacks) {
            ItemStack stack = observed.stack();
            entries.add(observed.networkKey().dimension() + ":" + observed.networkKey().position()
                    + ":" + ForgeRegistries.ITEMS.getKey(stack.getItem())
                    + "#" + stack.getCount()
                    + "#" + (stack.hasTag() ? stack.getTag() : ""));
        }
        Collections.sort(entries);
        return String.join(";", entries);
    }

    record NetworkStack(AccessibleNetwork.NetworkKey networkKey, ItemStack stack) {
    }
}
