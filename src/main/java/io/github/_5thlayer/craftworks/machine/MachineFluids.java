// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A machine's fluid boxes: its input boxes, then its output boxes, as its {@link FluidLayout} counts them. The Held recipe's {@code n}th
 * fluid ingredient goes in input box {@code n} and its {@code n}th fluid result in output box {@code n}.
 *
 * <p>Every box holds {@link FluidBoxes#VOLUME} mB. What may go in is the machine's to say: an input takes the
 * ingredient bound to it, an output the result bound to it, so the machine's own craft, a pipe that pushes and
 * its own pull meet the same filter.
 */
public final class MachineFluids extends FluidStacksResourceHandler {

    /** What the machine says about its boxes. */
    interface Owner {

        /** Whether input box {@code n} takes {@code resource}: it is the Held recipe's {@code n}th fluid ingredient. */
        boolean takesInput(int n, FluidResource resource);

        /** Whether output box {@code n} takes {@code resource}: it is the Held recipe's {@code n}th fluid result. */
        boolean makesOutput(int n, FluidResource resource);

        void changed();
    }

    private final FluidLayout layout;
    private final Owner owner;

    MachineFluids(FluidLayout layout, Owner owner) {
        super(layout.boxes(), FluidBoxes.VOLUME);
        this.layout = layout;
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

    /** What box {@code box} holds at most, in mB: {@link FluidBoxes#VOLUME}. */
    public int capacity(int box) {
        return FluidBoxes.VOLUME;
    }

    /** Whether some input box takes {@code resource}: it is a fluid ingredient of the Held recipe. */
    public boolean takesAnyInput(FluidResource resource) {
        for (int box = 0; box < layout.fluidInputs(); box++) {
            if (isValid(box, resource)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What box {@code box} reports as its volume for display, in mB: its capacity, or what it holds if that is
     * more, as a box saved under older rules can (an output box once grew past 2,000 mB). Only a display reads this; an overfull box takes nothing more in.
     */
    public int displayCapacity(int box) {
        return Math.max(capacity(box), getAmountAsInt(box));
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return layout.isInput(index) ? owner.takesInput(index, resource) : owner.makesOutput(layout.binding(index), resource);
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
        for (int box = 0; box < layout.boxes(); box++) {
            empty(box);
        }
    }
}
