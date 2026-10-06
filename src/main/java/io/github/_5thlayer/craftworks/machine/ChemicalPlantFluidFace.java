// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * What a Chemical Plant's Fluid Connections expose to NeoForge's fluid capability, the same on all four: its
 * four boxes, of which an input box is only filled, through the box's own filter, and an output box is only
 * drained. A connection has no direction of its own, but a box has: nothing is taken out of an ingredient
 * and nothing is put in a result.
 */
final class ChemicalPlantFluidFace implements ResourceHandler<FluidResource> {

    private final ResourceHandler<FluidResource> boxes;

    ChemicalPlantFluidFace(ResourceHandler<FluidResource> boxes) {
        this.boxes = boxes;
    }

    @Override
    public int size() {
        return boxes.size();
    }

    @Override
    public FluidResource getResource(int index) {
        return boxes.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return boxes.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return boxes.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return ChemicalPlantFluids.isInput(index) && boxes.isValid(index, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return ChemicalPlantFluids.isInput(index) ? boxes.insert(index, resource, amount, transaction) : 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return ChemicalPlantFluids.isInput(index) ? 0 : boxes.extract(index, resource, amount, transaction);
    }
}
