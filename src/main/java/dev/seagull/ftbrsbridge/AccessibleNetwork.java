package dev.seagull.ftbrsbridge;

import com.refinedmods.refinedstorage.api.network.INetwork;
import net.minecraft.resources.ResourceLocation;

record AccessibleNetwork(NetworkKey key, INetwork network) implements Comparable<AccessibleNetwork> {
    @Override
    public int compareTo(AccessibleNetwork other) {
        int dimensionOrder = key.dimension().toString().compareTo(other.key.dimension().toString());
        return dimensionOrder != 0 ? dimensionOrder : Long.compare(key.position(), other.key.position());
    }

    record NetworkKey(ResourceLocation dimension, long position) {
    }
}
