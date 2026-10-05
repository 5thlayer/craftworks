// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The Assembler's fluid capability, on its Fluid Connections: the box, filled by anything that pushes and
 * never drained from outside, since an Assembler makes no fluid results and pushes none.
 */
final class AssemblerFluidFace implements ResourceHandler<FluidResource> {

    private final ResourceHandler<FluidResource> box;

    AssemblerFluidFace(ResourceHandler<FluidResource> box) {
        this.box = box;
    }

    @Override
    public int size() {
        return box.size();
    }

    @Override
    public FluidResource getResource(int index) {
        return box.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return box.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return box.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return box.isValid(index, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return box.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }
}
