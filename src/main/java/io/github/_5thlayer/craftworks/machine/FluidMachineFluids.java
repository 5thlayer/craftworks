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
 * <p>An input box holds the {@link FluidBoxes} rule's 4 crafts' worth of the ingredient bound to it, an output
 * box the larger of 100 mB and 3 crafts' worth of the result bound to it, the first result taking the boxes
 * the recipe leaves unused unless the recipe is Pinned; with no Held recipe, or nothing bound to it, a box
 * holds its own volume. What may go in is the machine's to say: an input takes the ingredient bound to it, an output
 * the result bound to it, so the machine's own craft, a pipe that pushes and its own pull meet the same filter.
 */
public final class FluidMachineFluids extends FluidStacksResourceHandler {

    /** What the machine says about its boxes. */
    interface Owner {

        /** Whether input box {@code n} takes {@code resource}: it is the Held recipe's {@code n}th fluid ingredient. */
        boolean takesInput(int n, FluidResource resource);

        /** Whether output box {@code n} takes {@code resource}: it is the Held recipe's {@code n}th fluid result. */
        boolean makesOutput(int n, FluidResource resource);

        /** What input box {@code n} holds under the Held recipe, in mB. */
        int inputCapacity(int n);

        /** What output box {@code n} holds under the Held recipe, in mB. */
        int outputCapacity(int n);

        void changed();
    }

    private final FluidMachine description;
    private final Owner owner;

    FluidMachineFluids(FluidMachine description, Owner owner) {
        super(description.boxes(), FluidBoxes.INPUT_VOLUME);
        this.description = description;
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
        return description.isInput(box) ? owner.inputCapacity(description.binding(box)) : owner.outputCapacity(description.binding(box));
    }

    /**
     * What box {@code box} reports as its volume for display, in mB: its capacity, or what it holds if that is
     * more, as a box saved under older rules can. Only a display reads this; an overfull box takes nothing more in.
     */
    public int displayCapacity(int box) {
        return Math.max(capacity(box), getAmountAsInt(box));
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return capacity(index);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return description.isInput(index) ? owner.takesInput(index, resource) : owner.makesOutput(description.binding(index), resource);
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
        for (int box = 0; box < description.boxes(); box++) {
            empty(box);
        }
    }
}
