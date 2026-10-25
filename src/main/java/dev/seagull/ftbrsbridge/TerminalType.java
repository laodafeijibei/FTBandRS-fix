package dev.seagull.ftbrsbridge;

import com.refinedmods.refinedstorage.RS;
import com.refinedmods.refinedstorageaddons.RSAddons;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Optional;

enum TerminalType {
    WIRELESS_GRID("refinedstorage:wireless_grid", false),
    CREATIVE_WIRELESS_GRID("refinedstorage:creative_wireless_grid", true),
    WIRELESS_FLUID_GRID("refinedstorage:wireless_fluid_grid", false),
    CREATIVE_WIRELESS_FLUID_GRID("refinedstorage:creative_wireless_fluid_grid", true),
    WIRELESS_CRAFTING_GRID("refinedstorageaddons:wireless_crafting_grid", false),
    CREATIVE_WIRELESS_CRAFTING_GRID("refinedstorageaddons:creative_wireless_crafting_grid", true);

    private static final Map<ResourceLocation, TerminalType> BY_ID = Map.ofEntries(
            Map.entry(id("refinedstorage:wireless_grid"), WIRELESS_GRID),
            Map.entry(id("refinedstorage:creative_wireless_grid"), CREATIVE_WIRELESS_GRID),
            Map.entry(id("refinedstorage:wireless_fluid_grid"), WIRELESS_FLUID_GRID),
            Map.entry(id("refinedstorage:creative_wireless_fluid_grid"), CREATIVE_WIRELESS_FLUID_GRID),
            Map.entry(id("refinedstorageaddons:wireless_crafting_grid"), WIRELESS_CRAFTING_GRID),
            Map.entry(id("refinedstorageaddons:creative_wireless_crafting_grid"), CREATIVE_WIRELESS_CRAFTING_GRID)
    );

    private final ResourceLocation id;
    private final boolean creative;

    TerminalType(String id, boolean creative) {
        this.id = id(id);
        this.creative = creative;
    }

    static Optional<TerminalType> from(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(ForgeRegistries.ITEMS.getKey(stack.getItem())));
    }

    ResourceLocation id() {
        return id;
    }

    boolean hasEnoughEnergy(ItemStack stack) {
        if (creative || !usesEnergy()) {
            return true;
        }
        IEnergyStorage energy = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        return energy != null && energy.getEnergyStored() > openUsage();
    }

    private boolean usesEnergy() {
        return switch (this) {
            case WIRELESS_GRID -> RS.SERVER_CONFIG.getWirelessGrid().getUseEnergy();
            case WIRELESS_FLUID_GRID -> RS.SERVER_CONFIG.getWirelessFluidGrid().getUseEnergy();
            case WIRELESS_CRAFTING_GRID -> RSAddons.SERVER_CONFIG.getWirelessCraftingGrid().getUseEnergy();
            case CREATIVE_WIRELESS_GRID, CREATIVE_WIRELESS_FLUID_GRID, CREATIVE_WIRELESS_CRAFTING_GRID -> false;
        };
    }

    private int openUsage() {
        return switch (this) {
            case WIRELESS_GRID -> RS.SERVER_CONFIG.getWirelessGrid().getOpenUsage();
            case WIRELESS_FLUID_GRID -> RS.SERVER_CONFIG.getWirelessFluidGrid().getOpenUsage();
            case WIRELESS_CRAFTING_GRID -> RSAddons.SERVER_CONFIG.getWirelessCraftingGrid().getOpenUsage();
            case CREATIVE_WIRELESS_GRID, CREATIVE_WIRELESS_FLUID_GRID, CREATIVE_WIRELESS_CRAFTING_GRID -> 0;
        };
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
