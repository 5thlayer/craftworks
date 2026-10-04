// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.List;

/**
 * The crafts of one recipe in a Crafting Plan: what they eat, what they make, how many there are and
 * how long each one takes.
 *
 * <p>{@code inputs} and {@code outputs} are the whole batch's; {@code time} is <em>one</em> craft's,
 * and the Plan queue runs and delivers the {@code crafts} one at a time. One record rather than
 * one per craft keeps a large plan small on the player.
 *
 * <p>{@code remainders} are what the inputs leave behind once spent (an empty bucket), the batch's
 * total like the inputs. They come back to the player as each craft completes, and no later step counts
 * on them, which is what keeps the whole cost paid up front.
 *
 * <p>The inputs are carried on the step rather than derived from the recipe because the queue never
 * looks a recipe up: a plan is resolved once, paid for once, and then not re-resolved (ADR-0001).
 */
public record CraftStep(
        String recipe, List<ItemAmount> inputs, List<ItemAmount> outputs, int time, int crafts,
        List<ItemAmount> remainders) {

    public CraftStep {
        if (recipe == null || recipe.isBlank()) {
            throw new IllegalArgumentException("a craft step needs a recipe id");
        }
        if (time < 0) {
            throw new IllegalArgumentException("a craft step cannot take " + time + " ticks");
        }
        if (crafts < 1) {
            throw new IllegalArgumentException("a craft step cannot run " + crafts + " crafts");
        }
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        remainders = List.copyOf(remainders);
    }

    /** A batch whose inputs leave nothing behind. */
    public CraftStep(String recipe, List<ItemAmount> inputs, List<ItemAmount> outputs, int time, int crafts) {
        this(recipe, inputs, outputs, time, crafts, List.of());
    }

    /** A single craft. */
    public CraftStep(String recipe, List<ItemAmount> inputs, List<ItemAmount> outputs, int time) {
        this(recipe, inputs, outputs, time, 1);
    }

    /**
     * How much of a batch total the craft numbered {@code craft} (zero-based) accounts for.
     *
     * <p>Floors of the running share, so the crafts' amounts differ by at most one and always sum to
     * exactly the total: a tag ingredient drawn from two items rarely divides evenly.
     */
    public int share(int total, int craft) {
        return (int) ((long) total * (craft + 1) / crafts - (long) total * craft / crafts);
    }

    /** What is left of a batch total once {@code craftsDone} crafts have taken their share. */
    public int remaining(int total, int craftsDone) {
        return total - (int) ((long) total * craftsDone / crafts);
    }
}
