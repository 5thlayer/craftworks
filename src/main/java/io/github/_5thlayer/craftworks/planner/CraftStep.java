// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.ArrayList;
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
 * <p>{@code perCraft} is empty for an ordinary step. A Data-carrying step (ADR-0022) lists, for each of its
 * recipe's ingredients in order, how many one craft takes of it, and its {@code inputs} then list the draws
 * ingredient by ingredient, a key repeating where two ingredients drew it. That is what lets each craft be handed
 * the stacks its own ingredients took, not a floor of every key's total.
 *
 * <p>The inputs are carried on the step rather than derived from the recipe because the queue never
 * looks a recipe up: a plan is resolved once, paid for once, and then not re-resolved (ADR-0001).
 */
public record CraftStep(
        String recipe, List<ItemAmount> inputs, List<ItemAmount> outputs, int time, int crafts,
        List<ItemAmount> remainders, List<Integer> perCraft) {

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
        perCraft = List.copyOf(perCraft);
        if (!perCraft.isEmpty()) {
            long wanted = 0;
            for (int count : perCraft) wanted += (long) count * crafts;
            long drawn = 0;
            for (ItemAmount input : inputs) drawn += input.count();
            if (wanted != drawn) {
                throw new IllegalArgumentException("a Data-carrying step of " + recipe + " draws " + drawn
                        + " items for " + crafts + " crafts of " + perCraft);
            }
        }
    }

    /** An ordinary step, whose crafts take floors of each input's total. */
    public CraftStep(String recipe, List<ItemAmount> inputs, List<ItemAmount> outputs, int time, int crafts,
                     List<ItemAmount> remainders) {
        this(recipe, inputs, outputs, time, crafts, remainders, List.of());
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

    /** Whether the step is a Data-carrying recipe's (ADR-0022). */
    public boolean dataCarrying() {
        return !perCraft.isEmpty();
    }

    /**
     * What the craft numbered {@code craft} (zero-based) takes, by the keys the step planned. A Data-carrying
     * step hands each craft the draws of its own ingredients, in order; any other takes its share of each input.
     */
    public List<ItemAmount> craftInputs(int craft) {
        return dataCarrying() ? slice(craft, craft + 1) : shares(craft);
    }

    /** What the crafts after {@code craftsDone} of them still take, by the keys the step planned. */
    public List<ItemAmount> remainingInputs(int craftsDone) {
        if (dataCarrying()) return slice(craftsDone, crafts);
        List<ItemAmount> left = new ArrayList<>();
        for (ItemAmount input : inputs) {
            int count = remaining(input.count(), craftsDone);
            if (count > 0) left.add(new ItemAmount(input.item(), count));
        }
        return left;
    }

    private List<ItemAmount> shares(int craft) {
        List<ItemAmount> taken = new ArrayList<>();
        for (ItemAmount input : inputs) {
            int share = share(input.count(), craft);
            if (share > 0) taken.add(new ItemAmount(input.item(), share));
        }
        return taken;
    }

    /**
     * The draws of crafts {@code from} up to {@code to}, merged by key. Each ingredient's draws are a run of
     * {@code perCraft * crafts} units in {@code inputs}, and craft {@code n} owns the units
     * {@code [n * perCraft, (n + 1) * perCraft)} of its run.
     */
    private List<ItemAmount> slice(int from, int to) {
        ItemBag bag = new ItemBag();
        int[] cursor = {0, 0}; // the input being read, and how much of it is used
        for (int perOne : perCraft) {
            walk(cursor, (long) from * perOne, null);
            walk(cursor, (long) (to - from) * perOne, bag);
            walk(cursor, (long) (crafts - to) * perOne, null);
        }
        return bag.amounts();
    }

    /** Moves the cursor over {@code units} of the inputs, adding what it passes to {@code into} when given. */
    private void walk(int[] cursor, long units, ItemBag into) {
        long left = units;
        while (left > 0) {
            ItemAmount current = inputs.get(cursor[0]);
            int here = (int) Math.min(left, current.count() - cursor[1]);
            if (into != null) into.add(current.item(), here);
            left -= here;
            cursor[1] += here;
            if (cursor[1] == current.count()) {
                cursor[0]++;
                cursor[1] = 0;
            }
        }
    }
}
