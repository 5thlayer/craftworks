// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * The input fluid box of a tier 2 or 3 Assembler: one tank that takes only the Held recipe's fluid, and holds
 * 4 crafts' worth of it ({@link FluidBoxes#inputLimit}), at most a bucket's worth. What may go in is the
 * machine's to say, so a connection, a pipe that pushes and the Assembler's own pull all meet the same filter.
 */
public final class AssemblerFluidBox extends FluidStacksResourceHandler {

    private final AssemblerBlockEntity machine;

    AssemblerFluidBox(AssemblerBlockEntity machine) {
        super(1, FluidBoxes.INPUT_VOLUME);
        this.machine = machine;
    }

    /** What the box holds, empty when it is empty: its one tank, so no caller needs to know the index. */
    public FluidStack contents() {
        return getResource(0).toStack(getAmountAsInt(0));
    }

    /** Sets what the box holds; for the game tests, which put fluid in directly. */
    public void set(FluidStack contents) {
        set(0, FluidResource.of(contents), contents.getAmount());
    }

    /** What the box holds at most, in mB, as the Held recipe sizes it. */
    public int capacity() {
        return machine.fluidCapacity();
    }

    /**
     * What the box reports as its volume for display, in mB: its capacity, or what it holds if that is more, as a
     * box saved under older rules can. Only a display reads this; an overfull box takes nothing more in.
     */
    public int displayCapacity() {
        return Math.max(capacity(), getAmountAsInt(0));
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return capacity();
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
        if (!contents().isEmpty()) {
            set(FluidStack.EMPTY);
        }
    }
}
