// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Holds one fluid, or none, and shows it on NeoForge's fluid capability as a tank that gives it without
 * limit and accepts nothing. It never pushes: whatever takes fluid pulls it from here.
 */
public final class CreativeFluidSourceBlockEntity extends BlockEntity {

    private static final String FLUID_KEY = "fluid";

    /** What the capability shows for a fluid that is set: as full as an {@code int} amount gets. */
    private static final int FULL = Integer.MAX_VALUE;

    private FluidResource fluid = FluidResource.EMPTY;

    /** The capability: one tank that is full of the fluid, gives any amount of it and takes none. */
    private final ResourceHandler<FluidResource> face = new ResourceHandler<>() {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            return fluid;
        }

        @Override
        public long getAmountAsLong(int index) {
            return fluid.isEmpty() ? 0 : FULL;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return resource.equals(fluid) ? FULL : 0;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return false;
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return !fluid.isEmpty() && resource.equals(fluid) ? amount : 0;
        }
    };

    public CreativeFluidSourceBlockEntity(BlockPos pos, BlockState state) {
        super(Assemblers.CREATIVE_FLUID_SOURCE_ENTITY.get(), pos, state);
    }

    public ResourceHandler<FluidResource> fluidFace() {
        return face;
    }

    /** The fluid it gives, empty when it gives none. */
    public FluidResource fluid() {
        return fluid;
    }

    public void setFluid(FluidResource fluid) {
        if (!this.fluid.equals(fluid)) {
            this.fluid = fluid;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store(FLUID_KEY, FluidResource.OPTIONAL_CODEC, fluid);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fluid = input.read(FLUID_KEY, FluidResource.OPTIONAL_CODEC).orElse(FluidResource.EMPTY);
    }
}
