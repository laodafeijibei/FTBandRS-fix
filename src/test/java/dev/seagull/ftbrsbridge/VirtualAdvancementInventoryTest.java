package dev.seagull.ftbrsbridge;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualAdvancementInventoryTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exposesPlayerAndNetworkStacksWithoutMutatingSources() {
        ItemStack playerStack = new ItemStack(Items.STONE, 3);
        ItemStack backpackStack = new ItemStack(Items.GOLD_INGOT, 2);
        ItemStack networkStack = new ItemStack(Items.DIAMOND, 4);
        VirtualAdvancementInventory inventory = new VirtualAdvancementInventory(
                null,
                List.of(ItemStack.EMPTY, playerStack),
                List.of(backpackStack),
                List.of(networkStack)
        );

        assertEquals(4, inventory.getContainerSize());
        assertEquals(Items.STONE, inventory.getItem(1).getItem());
        assertEquals(Items.GOLD_INGOT, inventory.getItem(2).getItem());
        assertEquals(Items.DIAMOND, inventory.getItem(3).getItem());

        inventory.getItem(3).setCount(1);
        assertEquals(4, networkStack.getCount());
        assertEquals(3, playerStack.getCount());
        assertEquals(2, backpackStack.getCount());
    }
}
