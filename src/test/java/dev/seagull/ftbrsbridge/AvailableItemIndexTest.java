package dev.seagull.ftbrsbridge;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AvailableItemIndexTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void directItemTaskOnlyTestsSameItemCandidates() {
        AvailableItemIndex index = new AvailableItemIndex();
        index.add(new ItemStack(Items.DIAMOND, 3));
        index.add(new ItemStack(Items.STONE, 64));
        AtomicInteger calls = new AtomicInteger();

        long count = index.countMatching(
                new ItemStack(Items.DIAMOND),
                stack -> {
                    calls.incrementAndGet();
                    return stack.is(Items.DIAMOND);
                },
                false,
                64L
        );

        assertEquals(3L, count);
        assertEquals(1, calls.get());
        assertEquals(1, index.candidateCount(Items.DIAMOND));
        assertEquals(2, index.totalStackCount());
    }

    @Test
    void itemFilterStillTestsAllStacks() {
        AvailableItemIndex index = AvailableItemIndex.create(
                List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.STONE, 4)),
                new NetworkItemSnapshot(List.of())
        );
        AtomicInteger calls = new AtomicInteger();

        long count = index.countMatching(
                new ItemStack(Items.PAPER),
                stack -> {
                    calls.incrementAndGet();
                    return true;
                },
                true,
                20L
        );

        assertEquals(7L, count);
        assertEquals(2, calls.get());
    }

    @Test
    void matchingStopsAtTaskLimit() {
        AvailableItemIndex index = new AvailableItemIndex();
        index.add(new ItemStack(Items.DIAMOND, 64));
        index.add(new ItemStack(Items.DIAMOND, 64));
        AtomicInteger calls = new AtomicInteger();

        long count = index.countMatching(
                new ItemStack(Items.DIAMOND),
                stack -> {
                    calls.incrementAndGet();
                    return true;
                },
                false,
                10L
        );

        assertEquals(10L, count);
        assertEquals(1, calls.get());
    }
}
