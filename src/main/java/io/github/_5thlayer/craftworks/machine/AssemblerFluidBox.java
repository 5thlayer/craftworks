// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * The input fluid box of a tier 2 or 3 Assembler: one tank of {@link #CAPACITY} mB that takes only the
 * Held recipe's fluid. What may go in is the machine's to say, so a connection, a pipe that pushes and the
 * Assembler's own pull all meet the same filter.
 */
public final class AssemblerFluidBox extends FluidStacksResourceHandler {

    /** mB the box holds, a bucket's worth; a recipe needing more is refused. */
    public static final int CAPACITY = 1_000;

    private final AssemblerBlockEntity machine;

    AssemblerFluidBox(AssemblerBlockEntity machine) {
        super(1, CAPACITY);
        this.machine = machine;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return machine.takesFluid(resource);
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        machine.setChanged();
    }

    /** Empties the box: the Held recipe changed, or the tier has none. */
    void empty() {
        if (getAmountAsInt(0) > 0) {
            set(0, FluidResource.EMPTY, 0);
        }
    }
}
