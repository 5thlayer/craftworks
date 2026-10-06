// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jade;

import io.github._5thlayer.craftworks.machine.AssemblerBlock;
import io.github._5thlayer.craftworks.machine.AssemblerBlockEntity;
import io.github._5thlayer.craftworks.machine.CreativeEnergySourceBlockEntity;
import io.github._5thlayer.craftworks.machine.CreativeFluidSourceBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Shows an Assembler's Held recipe, state and energy in Jade's tooltip, and the Creative Energy Source's
 * energy as Infinite FE (#28) and the Creative Fluid Source's fluid as, say, "Water, infinite" (#32).
 *
 * <p>Jade finds this by its annotation and loads it only when Jade is installed; nothing else in the Mod
 * names a Jade type, so the Mod loads without it.
 *
 * <p>Every block of an Assembler's footprint shows the same, with no code of ours: Groundworks' own Jade
 * plugin reads a part as its origin, whose block entity and energy are the Assembler's. The item list is
 * Jade's own, from the item capability.
 */
@WailaPlugin
public final class CraftworksJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(AssemblerReport.INSTANCE, AssemblerBlockEntity.class);
        registration.registerEnergyStorage(InfiniteEnergy.INSTANCE, CreativeEnergySourceBlockEntity.class);
        registration.registerFluidStorage(InfiniteFluid.INSTANCE, CreativeFluidSourceBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(AssemblerReport.Client.INSTANCE, AssemblerBlock.class);
        registration.registerEnergyStorageClient(InfiniteEnergy.INSTANCE);
        registration.registerFluidStorageClient(InfiniteFluid.INSTANCE);
    }
}
