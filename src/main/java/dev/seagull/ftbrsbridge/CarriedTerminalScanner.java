package dev.seagull.ftbrsbridge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class CarriedTerminalScanner {
    private static final String CURIOS_MOD_ID = "curios";
    private static final String CURIOS_API = "top.theillusivec4.curios.api.CuriosApi";

    List<ItemStack> find(ServerPlayer player) {
        List<ItemStack> terminals = new ArrayList<>();
        player.getInventory().items.forEach(stack -> addIfSupported(terminals, stack));
        player.getInventory().armor.forEach(stack -> addIfSupported(terminals, stack));
        player.getInventory().offhand.forEach(stack -> addIfSupported(terminals, stack));
        findCurios(player, terminals);
        return terminals;
    }

    private void addIfSupported(List<ItemStack> terminals, ItemStack stack) {
        if (TerminalType.from(stack).isPresent()) {
            terminals.add(stack);
        }
    }

    private void findCurios(ServerPlayer player, List<ItemStack> terminals) {
        if (!ModList.get().isLoaded(CURIOS_MOD_ID)) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName(CURIOS_API);
            Method getCuriosInventory = apiClass.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            Object lazyOptional = getCuriosInventory.invoke(null, player);
            Method orElse = lazyOptional.getClass().getMethod("orElse", Object.class);
            Object handler = orElse.invoke(lazyOptional, new Object[]{null});
            if (handler != null) {
                collectCurioHandler(handler, terminals);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Diagnostics.warnThrottled("curios-scan", "Curios was detected but its inventory could not be scanned", exception);
        }
    }

    private void collectCurioHandler(Object handler, List<ItemStack> terminals) {
        try {
            Method getCurios = handler.getClass().getMethod("getCurios");
            Object result = getCurios.invoke(handler);
            if (!(result instanceof Map<?, ?> curios)) {
                return;
            }
            for (Object value : curios.values()) {
                Method getStacks = value.getClass().getMethod("getStacks");
                Object stacks = getStacks.invoke(value);
                if (!(stacks instanceof IItemHandler itemHandler)) {
                    continue;
                }
                for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                    addIfSupported(terminals, itemHandler.getStackInSlot(slot));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Diagnostics.warnThrottled("curios-handler", "Curios inventory handler could not be read", exception);
        }
    }
}
