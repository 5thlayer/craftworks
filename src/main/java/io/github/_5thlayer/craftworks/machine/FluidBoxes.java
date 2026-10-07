// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.ArrayList;
import java.util.List;

/**
 * How big a fluid box is: the figures the FactoryWorks Pack measured from Factorio (ADR-0017), as constants
 * beside {@link OverloadLimit}'s and not as config, for every fluid box in Craftworks, which are the
 * Assembler's.
 *
 * <p>An input box holds {@link #INPUT_VOLUME} mB, but is filled only until it holds {@link #INPUT_CRAFTS}
 * crafts' worth of the ingredient bound to it, whatever the crafting speed. An output box is
 * {@link #OUTPUT_BOX} mB, and holds the larger of that and {@link #OUTPUT_CRAFTS} crafts' worth of the result
 * bound to it; the first result also takes the boxes the recipe leaves unused, unless the recipe is Pinned.
 *
 * <p>Pure: no Minecraft types.
 */
public final class FluidBoxes {

    /** mB an input box holds; a recipe needing more of one fluid in a craft is refused. */
    public static final int INPUT_VOLUME = 1_000;
    /** Crafts' worth of its ingredient an input box is filled to. */
    static final int INPUT_CRAFTS = 4;
    /** mB an output box is before the recipe grows it. */
    public static final int OUTPUT_BOX = 100;
    /** Crafts' worth of its result an output box holds at least. */
    static final int OUTPUT_CRAFTS = 3;

    private FluidBoxes() {
    }

    /**
     * What an input box holds of an ingredient needing {@code perCraft} mB a craft: {@link #INPUT_CRAFTS}
     * crafts' worth, never more than the box. A machine pulls into it up to this, and a pipe or mod that pushes
     * is held to it too.
     */
    public static int inputLimit(int perCraft) {
        return (int) Math.min(INPUT_VOLUME, (long) INPUT_CRAFTS * perCraft);
    }

    /**
     * The volume of each output box a recipe's fluid results go in, in order: the larger of that result's box
     * and {@link #OUTPUT_CRAFTS} crafts' worth of it. The first result also takes the {@link #OUTPUT_BOX} of
     * each of the machine's {@code boxes} the recipe leaves unused, unless {@code pinned}. The Pack's
     * {@code OutputTankVolume}, with every box the same size.
     *
     * @param boxes   the machine's output boxes
     * @param amounts the mB a craft makes of each fluid result, at most {@code boxes} of them
     */
    public static List<Integer> outputVolumes(int boxes, List<Integer> amounts, boolean pinned) {
        List<Integer> volumes = new ArrayList<>(amounts.size());
        for (int index = 0; index < amounts.size(); index++) {
            long box = OUTPUT_BOX;
            if (index == 0 && !pinned) {
                box += (long) OUTPUT_BOX * Math.max(0, boxes - amounts.size());
            }
            volumes.add((int) Math.min(Integer.MAX_VALUE, Math.max(box, (long) OUTPUT_CRAFTS * amounts.get(index))));
        }
        return volumes;
    }
}
