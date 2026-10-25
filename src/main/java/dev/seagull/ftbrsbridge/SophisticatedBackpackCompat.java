package dev.seagull.ftbrsbridge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional Sophisticated Backpacks integration. The class names are kept out
 * of the normal class-linking path so the bridge still loads without the mod.
 */
final class SophisticatedBackpackCompat {
    private static final String BACKPACKS_MOD_ID = "sophisticatedbackpacks";
    private static final String CORE_MOD_ID = "sophisticatedcore";
    private static final String BACKPACK_NAMESPACE = "sophisticatedbackpacks";
    private static final String CAPABILITY_CLASS =
            "net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper";
    private static final String WRAPPER_CLASS =
            "net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper";
    private static final String CURIOS_MOD_ID = "curios";
    private static final String CURIOS_API = "top.theillusivec4.curios.api.CuriosApi";

    private Status status = Status.UNKNOWN;
    private Object capability;
    private Method getCapability;
    private Method getInventoryHandler;
    private Method getUpgradeHandler;

    void resetForServerLifecycle() {
        status = Status.UNKNOWN;
        capability = null;
        getCapability = null;
        getInventoryHandler = null;
        getUpgradeHandler = null;
    }

    BackpackDiscovery find(ServerPlayer player) {
        if (!ensureReady()) {
            return BackpackDiscovery.empty();
        }
        List<BackpackAccess> backpacks = new ArrayList<>();
        IdentityHashMap<ItemStack, Boolean> seen = new IdentityHashMap<>();
        boolean failure = false;
        boolean sawBackpack = false;

        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            BackpackResult result = tryAdd(
                    player.getInventory().items.get(slot),
                    "inventory[" + slot + "]",
                    seen,
                    backpacks
            );
            failure |= result.failure();
            sawBackpack |= result.sawBackpack();
        }
        for (int slot = 0; slot < player.getInventory().offhand.size(); slot++) {
            BackpackResult result = tryAdd(
                    player.getInventory().offhand.get(slot),
                    "offhand[" + slot + "]",
                    seen,
                    backpacks
            );
            failure |= result.failure();
            sawBackpack |= result.sawBackpack();
        }
        for (int slot = 0; slot < player.getInventory().armor.size(); slot++) {
            BackpackResult result = tryAdd(
                    player.getInventory().armor.get(slot),
                    "armor[" + slot + "]",
                    seen,
                    backpacks
            );
            failure |= result.failure();
            sawBackpack |= result.sawBackpack();
        }

        BackpackResult curiosResult = findCuriosBack(player, seen, backpacks);
        failure |= curiosResult.failure();
        sawBackpack |= curiosResult.sawBackpack();
        return new BackpackDiscovery(backpacks, failure, sawBackpack);
    }

    private boolean ensureReady() {
        if (status != Status.UNKNOWN) {
            return status == Status.ENABLED;
        }
        if (!ModList.get().isLoaded(BACKPACKS_MOD_ID) || !ModList.get().isLoaded(CORE_MOD_ID)) {
            status = Status.DISABLED;
            return false;
        }
        try {
            Class<?> capabilityClass = Class.forName(CAPABILITY_CLASS);
            Method getCapabilityInstance = capabilityClass.getMethod("getCapabilityInstance");
            capability = getCapabilityInstance.invoke(null);
            getCapability = ItemStack.class.getMethod("getCapability", Capability.class);
            Class<?> wrapperClass = Class.forName(WRAPPER_CLASS);
            getInventoryHandler = wrapperClass.getMethod("getInventoryHandler");
            getUpgradeHandler = wrapperClass.getMethod("getUpgradeHandler");
            status = Status.ENABLED;
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            status = Status.DISABLED;
            Diagnostics.warnThrottled(
                    "sophisticated-api",
                    "Sophisticated Backpacks API is incompatible; backpack integration is disabled",
                    exception
            );
            return false;
        }
    }

    private BackpackResult tryAdd(
            ItemStack stack,
            String location,
            IdentityHashMap<ItemStack, Boolean> seen,
            List<BackpackAccess> backpacks
    ) {
        if (stack.isEmpty() || !isBackpackItem(stack)) {
            return BackpackResult.NONE;
        }
        if (seen.put(stack, Boolean.TRUE) != null) {
            return new BackpackResult(false, true);
        }
        try {
            Object wrapper = resolveWrapper(stack);
            if (wrapper == null) {
                return new BackpackResult(true, true);
            }
            Object inventory = getInventoryHandler.invoke(wrapper);
            Object upgrades = getUpgradeHandler.invoke(wrapper);
            if (!(inventory instanceof IItemHandler mainHandler)
                    || !(upgrades instanceof IItemHandler upgradeHandler)) {
                throw new IllegalStateException("Backpack handlers do not implement IItemHandler");
            }
            backpacks.add(new BackpackAccess(stack, mainHandler, upgradeHandler, location));
            return new BackpackResult(false, true);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Diagnostics.warnThrottled(
                    "sophisticated-backpack-" + location,
                    "Could not read Sophisticated Backpack at {}",
                    location,
                    exception
            );
            return new BackpackResult(true, true);
        }
    }

    private Object resolveWrapper(ItemStack stack) throws ReflectiveOperationException {
        Object optional = getCapability.invoke(stack, capability);
        if (!(optional instanceof LazyOptional<?> lazyOptional)) {
            throw new IllegalStateException("Backpack capability did not return LazyOptional");
        }
        return lazyOptional.orElse(null);
    }

    private boolean isBackpackItem(ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && BACKPACK_NAMESPACE.equals(key.getNamespace());
    }

    private BackpackResult findCuriosBack(
            ServerPlayer player,
            IdentityHashMap<ItemStack, Boolean> seen,
            List<BackpackAccess> backpacks
    ) {
        if (!ModList.get().isLoaded(CURIOS_MOD_ID)) {
            return BackpackResult.NONE;
        }
        try {
            Class<?> apiClass = Class.forName(CURIOS_API);
            Method getCuriosInventory = apiClass.getMethod(
                    "getCuriosInventory",
                    net.minecraft.world.entity.LivingEntity.class
            );
            Object lazyOptional = getCuriosInventory.invoke(null, player);
            Method orElse = lazyOptional.getClass().getMethod("orElse", Object.class);
            Object handler = orElse.invoke(lazyOptional, new Object[]{null});
            if (handler == null) {
                return BackpackResult.NONE;
            }
            Method getCurios = handler.getClass().getMethod("getCurios");
            Object result = getCurios.invoke(handler);
            if (!(result instanceof Map<?, ?> curios)) {
                return BackpackResult.NONE;
            }
            Object backHandler = curios.get("back");
            if (backHandler == null) {
                return BackpackResult.NONE;
            }
            Method getStacks = backHandler.getClass().getMethod("getStacks");
            Object stacks = getStacks.invoke(backHandler);
            if (!(stacks instanceof IItemHandler itemHandler)) {
                return BackpackResult.NONE;
            }
            boolean failure = false;
            boolean saw = false;
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                BackpackResult one = tryAdd(
                        itemHandler.getStackInSlot(slot),
                        "curios.back[" + slot + "]",
                        seen,
                        backpacks
                );
                failure |= one.failure();
                saw |= one.sawBackpack();
            }
            return new BackpackResult(failure, saw);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Diagnostics.warnThrottled(
                    "sophisticated-curios-back",
                    "Could not scan Sophisticated Backpacks in Curios back slot",
                    exception
            );
            return new BackpackResult(true, false);
        }
    }

    private enum Status {
        UNKNOWN,
        ENABLED,
        DISABLED
    }

    private record BackpackResult(boolean failure, boolean sawBackpack) {
        private static final BackpackResult NONE = new BackpackResult(false, false);
    }
}

record BackpackDiscovery(List<BackpackAccess> backpacks, boolean readFailure, boolean sawBackpack) {
    static BackpackDiscovery empty() {
        return new BackpackDiscovery(List.of(), false, false);
    }
}
