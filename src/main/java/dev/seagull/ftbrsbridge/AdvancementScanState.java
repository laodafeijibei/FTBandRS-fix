package dev.seagull.ftbrsbridge;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Tracks external inventory deltas needed by inventory_changed criteria. */
final class AdvancementScanState {
    private final Map<ExternalStackKey, Long> externalCounts;
    private final Map<ExternalStackKey, ItemStack> representatives;
    private final List<ItemStack> backpackItems;
    private final List<ItemStack> networkItems;
    private final String playerFingerprint;

    private AdvancementScanState(
            Map<ExternalStackKey, Long> externalCounts,
            Map<ExternalStackKey, ItemStack> representatives,
            List<ItemStack> backpackItems,
            List<ItemStack> networkItems,
            String playerFingerprint
    ) {
        this.externalCounts = Map.copyOf(externalCounts);
        this.representatives = Map.copyOf(representatives);
        this.backpackItems = List.copyOf(backpackItems);
        this.networkItems = List.copyOf(networkItems);
        this.playerFingerprint = playerFingerprint;
    }

    static AdvancementScanState capture(
            List<ItemStack> playerItems,
            NetworkItemSnapshot networkSnapshot
    ) {
        return capture(playerItems, List.of(), networkSnapshot);
    }

    static AdvancementScanState capture(
            List<ItemStack> playerItems,
            List<ItemStack> backpackItems,
            NetworkItemSnapshot networkSnapshot
    ) {
        Map<ExternalStackKey, Long> counts = new HashMap<>();
        Map<ExternalStackKey, ItemStack> representatives = new HashMap<>();
        for (ItemStack stack : backpackItems) {
            addExternal(
                    counts,
                    representatives,
                    new ExternalStackKey("backpack", null, stack.getItem(), tagKey(stack)),
                    stack
            );
        }
        for (NetworkItemSnapshot.NetworkStack observed : networkSnapshot.stacks()) {
            ItemStack stack = observed.stack();
            addExternal(
                    counts,
                    representatives,
                    new ExternalStackKey(
                            "network",
                            observed.networkKey(),
                            stack.getItem(),
                            tagKey(stack)
                    ),
                    stack
            );
        }
        return new AdvancementScanState(
                counts,
                representatives,
                backpackItems,
                networkSnapshot.itemStacks(),
                fingerprint(playerItems)
        );
    }

    List<ItemStack> triggerStacksSince(AdvancementScanState previous) {
        if (previous == null) {
            return new ArrayList<>(representatives.values());
        }
        List<ItemStack> changed = new ArrayList<>();
        for (Map.Entry<ExternalStackKey, Long> entry : externalCounts.entrySet()) {
            if (!Objects.equals(previous.externalCounts.get(entry.getKey()), entry.getValue())) {
                changed.add(representatives.get(entry.getKey()));
            }
        }
        boolean removed = !externalCounts.keySet().containsAll(previous.externalCounts.keySet());
        boolean playerChanged = !playerFingerprint.equals(previous.playerFingerprint);
        if (changed.isEmpty() && (removed || playerChanged)) {
            ItemStack fallback = firstExternalItem();
            changed.add(fallback == null ? ItemStack.EMPTY : fallback);
        }
        return changed;
    }

    List<ItemStack> backpackItems() {
        return backpackItems;
    }

    List<ItemStack> networkItems() {
        return networkItems;
    }

    private ItemStack firstExternalItem() {
        if (!backpackItems.isEmpty()) {
            return backpackItems.get(0);
        }
        return networkItems.isEmpty() ? null : networkItems.get(0);
    }

    private static void addExternal(
            Map<ExternalStackKey, Long> counts,
            Map<ExternalStackKey, ItemStack> representatives,
            ExternalStackKey key,
            ItemStack stack
    ) {
        counts.merge(key, (long) stack.getCount(), AdvancementScanState::saturatedAdd);
        representatives.putIfAbsent(key, stack.copy());
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static String fingerprint(List<ItemStack> stacks) {
        List<String> entries = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            entries.add(stackKey(stack));
        }
        Collections.sort(entries);
        return String.join(";", entries);
    }

    private static String stackKey(ItemStack stack) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()))
                + "#" + stack.getCount()
                + "#" + tagKey(stack);
    }

    private static String tagKey(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().toString() : "";
    }

    private record ExternalStackKey(
            String source,
            AccessibleNetwork.NetworkKey networkKey,
            Item item,
            String tag
    ) {
    }
}
