package dev.seagull.ftbrsbridge;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Per-scan item index that avoids re-reading the complete RS cache per task. */
final class AvailableItemIndex {
    private final Map<Item, List<ItemStack>> stacksByItem = new IdentityHashMap<>();
    private final List<ItemStack> allStacks = new ArrayList<>();

    static AvailableItemIndex create(
            Iterable<ItemStack> playerItems,
            NetworkItemSnapshot networkSnapshot
    ) {
        AvailableItemIndex index = new AvailableItemIndex();
        playerItems.forEach(index::add);
        networkSnapshot.itemStacks().forEach(index::add);
        return index;
    }

    void add(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        allStacks.add(stack);
        stacksByItem.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>()).add(stack);
    }

    long countMatching(
            ItemStack taskStack,
            Predicate<ItemStack> matcher,
            boolean itemFilter,
            long limit
    ) {
        if (limit <= 0L) {
            return 0L;
        }
        Iterable<ItemStack> candidates = itemFilter
                ? allStacks
                : stacksByItem.getOrDefault(taskStack.getItem(), List.of());
        long total = 0L;
        for (ItemStack stack : candidates) {
            if (!matcher.test(stack)) {
                continue;
            }
            long remaining = limit - total;
            if (stack.getCount() >= remaining) {
                return limit;
            }
            total += stack.getCount();
        }
        return total;
    }

    int candidateCount(Item item) {
        return stacksByItem.getOrDefault(item, List.of()).size();
    }

    int totalStackCount() {
        return allStacks.size();
    }
}
