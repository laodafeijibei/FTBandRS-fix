package dev.seagull.ftbrsbridge;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancementScanStateTest {
    private static final AccessibleNetwork.NetworkKey NETWORK =
            new AccessibleNetwork.NetworkKey(new ResourceLocation("minecraft", "overworld"), 1L);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void initialScanTriggersEveryNetworkTypeAndUnchangedScanDoesNothing() {
        AdvancementScanState state = state(
                List.of(ItemStack.EMPTY),
                stack(Items.DIAMOND, 3),
                stack(Items.STONE, 4)
        );

        assertEquals(2, state.triggerStacksSince(null).size());
        assertTrue(state.triggerStacksSince(state).isEmpty());
    }

    @Test
    void countChangeOnlyTriggersChangedNetworkType() {
        AdvancementScanState previous = state(List.of(ItemStack.EMPTY), stack(Items.DIAMOND, 3));
        AdvancementScanState current = state(List.of(ItemStack.EMPTY), stack(Items.DIAMOND, 4));

        List<ItemStack> changed = current.triggerStacksSince(previous);

        assertEquals(1, changed.size());
        assertEquals(Items.DIAMOND, changed.get(0).getItem());
        assertEquals(4, changed.get(0).getCount());
    }

    @Test
    void playerInventoryChangeUsesSingleFallbackTrigger() {
        AdvancementScanState previous = state(List.of(ItemStack.EMPTY), stack(Items.DIAMOND, 3));
        AdvancementScanState current = state(
                List.of(stack(Items.STONE, 1)),
                stack(Items.DIAMOND, 3)
        );

        assertEquals(1, current.triggerStacksSince(previous).size());
    }

    @Test
    void networkRemovalUsesSingleFallbackTrigger() {
        AdvancementScanState previous = state(
                List.of(ItemStack.EMPTY),
                stack(Items.DIAMOND, 3),
                stack(Items.STONE, 4)
        );
        AdvancementScanState current = state(List.of(ItemStack.EMPTY), stack(Items.DIAMOND, 3));

        List<ItemStack> changed = current.triggerStacksSince(previous);

        assertEquals(1, changed.size());
        assertEquals(Items.DIAMOND, changed.get(0).getItem());
    }

    @Test
    void backpackChangeTriggersOnlyTheChangedBackpackItem() {
        AdvancementScanState previous = state(
                List.of(ItemStack.EMPTY),
                List.of(stack(Items.GOLD_INGOT, 2)),
                stack(Items.DIAMOND, 3)
        );
        AdvancementScanState current = state(
                List.of(ItemStack.EMPTY),
                List.of(stack(Items.GOLD_INGOT, 4)),
                stack(Items.DIAMOND, 3)
        );

        List<ItemStack> changed = current.triggerStacksSince(previous);

        assertEquals(1, changed.size());
        assertEquals(Items.GOLD_INGOT, changed.get(0).getItem());
        assertEquals(4, changed.get(0).getCount());
    }

    private static AdvancementScanState state(List<ItemStack> playerItems, ItemStack... networkItems) {
        return state(playerItems, List.of(), networkItems);
    }

    private static AdvancementScanState state(
            List<ItemStack> playerItems,
            List<ItemStack> backpackItems,
            ItemStack... networkItems
    ) {
        List<NetworkItemSnapshot.NetworkStack> observed = List.of(networkItems).stream()
                .map(stack -> new NetworkItemSnapshot.NetworkStack(NETWORK, stack))
                .toList();
        return AdvancementScanState.capture(
                playerItems,
                backpackItems,
                new NetworkItemSnapshot(observed)
        );
    }

    private static ItemStack stack(net.minecraft.world.item.Item item, int count) {
        return new ItemStack(item, count);
    }
}
