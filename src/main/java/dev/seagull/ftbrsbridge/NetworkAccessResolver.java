package dev.seagull.ftbrsbridge;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.INetworkNodeGraphEntry;
import com.refinedmods.refinedstorage.api.network.IWirelessTransmitter;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.api.network.security.Permission;
import com.refinedmods.refinedstorage.item.NetworkItem;
import com.refinedmods.refinedstorage.util.NetworkUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class NetworkAccessResolver {
    List<AccessibleNetwork> resolve(MinecraftServer server, ServerPlayer player, List<ItemStack> terminals) {
        Map<AccessibleNetwork.NetworkKey, AccessibleNetwork> networks = new HashMap<>();
        for (ItemStack terminal : terminals) {
            TerminalType terminalType = TerminalType.from(terminal).orElse(null);
            if (terminalType == null || !terminalType.hasEnoughEnergy(terminal)) {
                continue;
            }
            AccessibleNetwork accessible = resolveTerminal(server, player, terminal);
            if (accessible != null) {
                networks.putIfAbsent(accessible.key(), accessible);
            }
        }
        List<AccessibleNetwork> result = new ArrayList<>(networks.values());
        result.sort(null);
        return result;
    }

    private AccessibleNetwork resolveTerminal(MinecraftServer server, ServerPlayer player, ItemStack terminal) {
        if (!NetworkItem.isValid(terminal)) {
            return null;
        }
        ResourceKey<Level> dimension = NetworkItem.getDimension(terminal);
        if (dimension == null) {
            return null;
        }
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return null;
        }
        BlockPos position = new BlockPos(
                NetworkItem.getX(terminal),
                NetworkItem.getY(terminal),
                NetworkItem.getZ(terminal)
        );
        if (!level.isLoaded(position)) {
            return null;
        }
        INetworkNode boundNode = NetworkUtils.getNodeFromBlockEntity(level.getBlockEntity(position));
        if (boundNode == null) {
            return null;
        }
        INetwork network = NetworkUtils.getNetworkFromNode(boundNode);
        if (network == null || !network.canRun() || !hasPermissions(network, player) || !hasReachableTransmitter(network, player)) {
            return null;
        }
        ResourceLocation networkDimension = network.getLevel().dimension().location();
        AccessibleNetwork.NetworkKey key = new AccessibleNetwork.NetworkKey(networkDimension, network.getPosition().asLong());
        return new AccessibleNetwork(key, network);
    }

    private boolean hasPermissions(INetwork network, ServerPlayer player) {
        return network.getSecurityManager().hasPermission(Permission.MODIFY, player)
                && network.getSecurityManager().hasPermission(Permission.EXTRACT, player);
    }

    private boolean hasReachableTransmitter(INetwork network, ServerPlayer player) {
        for (INetworkNodeGraphEntry entry : network.getNodeGraph().all()) {
            INetworkNode node = entry.getNode();
            if (!node.isActive() || !(node instanceof IWirelessTransmitter transmitter)) {
                continue;
            }
            if (transmitter.getDimension().equals(player.level().dimension())) {
                double range = transmitter.getRange();
                if (player.position().distanceToSqr(
                        transmitter.getOrigin().getX(),
                        transmitter.getOrigin().getY(),
                        transmitter.getOrigin().getZ()
                ) < range * range) {
                    return true;
                }
            } else if (InfinityBoosterCompat.hasDimensionCard(node)) {
                return true;
            }
        }
        return false;
    }
}
