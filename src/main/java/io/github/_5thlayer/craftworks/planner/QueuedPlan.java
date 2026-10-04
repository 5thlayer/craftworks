// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Crafting Plan on the Plan queue, and everything about it that survives a logout: which step
 * it is on, how many of that step's crafts are done, how far into the next one, and what it is holding.
 *
 * <p>The buffer is the plan's own pocket. It starts as the cost the queue took, loses a step's inputs
 * and gains its outputs as each craft finishes, and hands the player whatever no remaining step needs.
 * So it is exactly "the unspent cost plus the intermediates already made", which is what a cancel
 * refunds, without the queue having to reconstruct it.
 *
 * <p>Mutable, and deliberately not a record: it is ticked in place twenty times a second, and the copy
 * a record would demand per tick buys nothing.
 */
public final class QueuedPlan {

    private final CraftingPlan plan;
    private final ItemBag buffer;
    private int stepIndex;
    private int craftsDone;
    private int progressTicks;

    QueuedPlan(CraftingPlan plan, ItemBag buffer, int stepIndex, int craftsDone, int progressTicks) {
        this.plan = plan;
        this.buffer = buffer.copy();
        this.stepIndex = stepIndex;
        this.craftsDone = craftsDone;
        this.progressTicks = progressTicks;
    }

    /** Restores one, which is what the player's attachment does on login. */
    public static QueuedPlan restored(
            CraftingPlan plan, Map<String, Integer> buffer, int stepIndex, int craftsDone, int progressTicks) {
        return new QueuedPlan(plan, ItemBag.of(buffer), stepIndex, craftsDone, progressTicks);
    }

    public CraftingPlan plan() {
        return plan;
    }

    /** What the plan is holding. A copy: the queue is the only thing that may change it. */
    public Map<String, Integer> buffer() {
        return buffer.asMap();
    }

    public int stepIndex() {
        return stepIndex;
    }

    /** How many of the current step's crafts are finished. */
    public int craftsDone() {
        return craftsDone;
    }

    public int progressTicks() {
        return progressTicks;
    }

    /** The step being crafted, or null once the last one is done. */
    public CraftStep currentStep() {
        return stepIndex < plan.steps().size() ? plan.steps().get(stepIndex) : null;
    }

    /**
     * How much of the plan's {@code amount} is still to be made, counting down one craft at a time.
     *
     * <p>Counted over the steps that make the root, one on a fresh plan and several on a row that
     * grew, so crafting an intermediate leaves it unchanged. Proportional rather than one per craft
     * because a recipe can make more than one of its item.
     */
    public int remainingAmount() {
        long total = 0;
        for (CraftStep step : plan.steps()) {
            if (step.recipe().equals(rootRecipe())) total += step.crafts();
        }
        return total == 0 ? 0 : (int) ((long) plan.amount() * remainingRootCrafts() / total);
    }

    /** How many crafts of the row's final recipe are still to run: what a cancel counts in. */
    public int remainingRootCrafts() {
        int left = 0;
        for (int i = stepIndex; i < plan.steps().size(); i++) {
            CraftStep step = plan.steps().get(i);
            if (!step.recipe().equals(rootRecipe())) continue;
            left += step.crafts() - (i == stepIndex ? craftsDone : 0);
        }
        return left;
    }

    private String rootRecipe() {
        return plan.steps().isEmpty() ? "" : plan.steps().getLast().recipe();
    }

    /** What the current step still has to make of its first output, or 0 once the last step is done. */
    public int remainingStepAmount() {
        CraftStep step = currentStep();
        if (step == null || step.outputs().isEmpty()) return 0;
        return step.remaining(step.outputs().get(0).count(), craftsDone);
    }

    /** How far {@link #progress()} moves per tick while running, so a client can draw between syncs. */
    public float progressPerTick() {
        CraftStep step = currentStep();
        if (step == null || step.time() <= 0) return 0.0f;
        return 1.0f / step.time();
    }

    /** Zero to one, for a progress bar; one when there is nothing left to craft. */
    public float progress() {
        CraftStep step = currentStep();
        if (step == null || step.time() <= 0) return 1.0f;
        return Math.min(1.0f, (float) progressTicks / step.time());
    }

    void advanceTick() {
        progressTicks++;
    }

    /**
     * Consumes one craft's share of the step's inputs and banks its share of the outputs and the
     * remainders, moving to the next step once the last craft is done. The queue then hands the player
     * whatever no remaining step needs, which the remainders always are.
     *
     * <p>It throws rather than under-consuming when the buffer cannot cover the craft. A Crafting Plan
     * is complete by construction and was paid for whole when queued, so a step that cannot be fed is
     * a Resolver bug, and the alternative to a loud failure is a plan that silently crafts something
     * out of nothing.
     */
    void completeCraft() {
        CraftStep step = plan.steps().get(stepIndex);
        for (ItemAmount input : step.inputs()) {
            int wanted = step.share(input.count(), craftsDone);
            if (buffer.count(input.item()) < wanted) {
                throw new IllegalStateException("Plan " + plan.id() + " step " + stepIndex
                        + " wants " + wanted + " " + input.item()
                        + " but the plan is holding " + buffer.count(input.item()));
            }
        }
        for (ItemAmount input : step.inputs()) {
            buffer.remove(input.item(), step.share(input.count(), craftsDone));
        }
        for (ItemAmount output : step.outputs()) {
            buffer.add(output.item(), step.share(output.count(), craftsDone));
        }
        for (ItemAmount remainder : step.remainders()) {
            buffer.add(remainder.item(), step.share(remainder.count(), craftsDone));
        }
        craftsDone++;
        progressTicks = 0;
        if (craftsDone >= step.crafts()) {
            stepIndex++;
            craftsDone = 0;
        }
    }

    /**
     * This row with another plan's steps appended and its cost added to the buffer, keeping the row's
     * id and how far it has got. The amount and raw cost are summed so the row reads, and refunds, as
     * one plan.
     */
    QueuedPlan extendedBy(CraftingPlan more) {
        ItemBag cost = ItemBag.ofAmounts(plan.rawCost());
        ItemBag held = buffer.copy();
        for (ItemAmount owed : more.rawCost()) {
            cost.add(owed.item(), owed.count());
            held.add(owed.item(), owed.count());
        }
        List<CraftStep> steps = new ArrayList<>(plan.steps());
        steps.addAll(more.steps());
        return new QueuedPlan(new CraftingPlan(plan.id(), plan.rootItem(), plan.amount() + more.amount(),
                cost.amounts(), steps), held, stepIndex, craftsDone, progressTicks);
    }

    /** Everything the plan is holding, as a list that survives the bag being changed. */
    List<ItemAmount> held() {
        return buffer.amounts();
    }

    void takeFromBuffer(String item, int count) {
        buffer.remove(item, count);
    }

    boolean finished() {
        return stepIndex >= plan.steps().size();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QueuedPlan that
                && stepIndex == that.stepIndex
                && craftsDone == that.craftsDone
                && progressTicks == that.progressTicks
                && plan.equals(that.plan)
                && buffer.equals(that.buffer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plan, buffer, stepIndex, craftsDone, progressTicks);
    }

    @Override
    public String toString() {
        return "QueuedPlan[" + plan.rootItem() + " x" + plan.amount()
                + ", step " + stepIndex + "/" + plan.steps().size() + " craft " + craftsDone
                + ", " + progressTicks + " ticks, holding " + buffer + "]";
    }
}
