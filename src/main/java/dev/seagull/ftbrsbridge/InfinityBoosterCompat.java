package dev.seagull.ftbrsbridge;

import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class InfinityBoosterCompat {
    private static final String MOD_ID = "rsinfinitybooster";
    private static final ResourceLocation DIMENSION_CARD = new ResourceLocation("rsinfinitybooster:dimension_card");
    private static final Map<Class<?>, Method> UPGRADE_METHODS = new ConcurrentHashMap<>();

    private InfinityBoosterCompat() {
    }

    static boolean hasDimensionCard(INetworkNode node) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return false;
        }
        try {
            Method getUpgrades = UPGRADE_METHODS.computeIfAbsent(node.getClass(), InfinityBoosterCompat::findUpgradeMethod);
            if (getUpgrades == null) {
                return false;
            }
            Object upgrades = getUpgrades.invoke(node);
            if (!(upgrades instanceof IItemHandler itemHandler)) {
                return false;
            }
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                ItemStack stack = itemHandler.getStackInSlot(slot);
                if (!stack.isEmpty() && DIMENSION_CARD.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Diagnostics.warnThrottled("infinity-booster", "Could not inspect RSInfinityBooster transmitter upgrades", exception);
        }
        return false;
    }

    private static Method findUpgradeMethod(Class<?> type) {
        try {
            return type.getMethod("getUpgrades");
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }
}
