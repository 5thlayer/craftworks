// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A fluid machine's boxes: its input boxes, then its output boxes. The Held recipe's {@code n}th
 * fluid ingredient goes in input box {@code n} and its {@code n}th fluid result in output box {@code n}.
 *
 * <p>An input box holds {@link #INPUT_CAPACITY} mB, like an Assembler's. An output box holds the larger of
 * that and the Overload Limit's crafts' worth of the result bound to it, so the machine makes a few crafts
 * before it stalls on a full box ({@link OverloadLimit#outputBox}); with no Held recipe, or no result bound to
 * it, 1,000 mB. What may go in is the machine's to say: an input takes the ingredient bound to it, an output
 * the result bound to it, so the machine's own craft, a pipe that pushes and its own pull meet the same filter.
 */
public final class FluidMachineFluids extends FluidStacksResourceHandler {

    /** mB an input box holds, and an output box at least: a bucket's worth. */
    public static final int INPUT_CAPACITY = AssemblerFluidBox.CAPACITY;

    /** What the machine says about its boxes. */
    interface Owner {

        /** Whether input box {@code n} takes {@code resource}: it is the Held recipe's {@code n}th fluid ingredient. */
        boolean takesInput(int n, FluidResource resource);

        /** Whether output box {@code n} takes {@code resource}: it is the Held recipe's {@code n}th fluid result. */
        boolean makesOutput(int n, FluidResource resource);

        /** What output box {@code n} holds under the Held recipe, in mB. */
        int outputCapacity(int n);

        void changed();
    }

    private final FluidMachine machine;
    private final Owner owner;

    FluidMachineFluids(FluidMachine machine, Owner owner) {
        super(machine.boxes(), INPUT_CAPACITY);
        this.machine = machine;
        this.owner = owner;
    }

    /** What box {@code box} holds, empty when it is empty. */
    public FluidStack contents(int box) {
        return getResource(box).toStack(getAmountAsInt(box));
    }

    /** Sets what box {@code box} holds; for the game tests, which put fluid in directly. */
    public void set(int box, FluidStack contents) {
        set(box, FluidResource.of(contents), contents.getAmount());
    }

    /** What box {@code box} holds at most, in mB, as the Held recipe sizes it. */
    public int capacity(int box) {
        return machine.isInput(box) ? INPUT_CAPACITY : owner.outputCapacity(machine.binding(box));
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return capacity(index);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return machine.isInput(index) ? owner.takesInput(index, resource) : owner.makesOutput(machine.binding(index), resource);
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        owner.changed();
    }

    /** Empties box {@code box}. */
    void empty(int box) {
        if (!getResource(box).isEmpty()) {
            set(box, FluidResource.EMPTY, 0);
        }
    }

    /** Empties every box: the Held recipe changed, or the machine cannot run it. */
    void emptyAll() {
        for (int box = 0; box < machine.boxes(); box++) {
            empty(box);
        }
    }
}
