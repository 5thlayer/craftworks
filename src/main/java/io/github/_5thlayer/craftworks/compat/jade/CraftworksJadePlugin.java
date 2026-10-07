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
 * Shows an Assembler's Held recipe, state and energy in Jade's tooltip, with the fluid boxes of tiers 2 and 3 as
 * Jade's own fluid bars (#25, #26, #27, #40); the Creative Energy Source's energy as Infinite FE (#28); and the
 * Creative Fluid Source's fluid as, say, "Water, infinite" (#32).
 *
 * <p>Jade finds this by its annotation and loads it only when Jade is installed; nothing else in the Mod
 * names a Jade type, so the Mod loads without it.
 *
 * <p>Every block of a machine's footprint shows the same, with no code of ours: Groundworks' own Jade
 * plugin reads a part as its origin, whose block entity and energy are the Assembler's. The item list is
 * Jade's own, from the item capability.
 */
@WailaPlugin
public final class CraftworksJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(HeldMachineReport.INSTANCE, AssemblerBlockEntity.class);
        registration.registerEnergyStorage(InfiniteEnergy.INSTANCE, CreativeEnergySourceBlockEntity.class);
        registration.registerFluidStorage(InfiniteFluid.INSTANCE, CreativeFluidSourceBlockEntity.class);
        registration.registerFluidStorage(AssemblerFluidView.INSTANCE, AssemblerBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(HeldMachineReport.Client.INSTANCE, AssemblerBlock.class);
        registration.registerEnergyStorageClient(InfiniteEnergy.INSTANCE);
        registration.registerFluidStorageClient(InfiniteFluid.INSTANCE);
        registration.registerFluidStorageClient(AssemblerFluidView.INSTANCE);
    }
}
