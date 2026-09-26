// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Assembler queue: the serial list of Crafting Plans, ticked whether or not anybody is looking at
 * it (ADR-0001).
 *
 * <p>Three rules, and they are the whole class:
 *
 * <ul>
 *   <li><b>Queueing pays for everything.</b> A plan's whole raw cost leaves the player's items when it
 *       is queued, so nothing can change underneath a running plan and nothing is ever re-validated.
 *   <li><b>One at a time, and a paused head does not step aside.</b> Serial crafting is what sets the
 *       pace, so letting a stalled plan be overtaken would quietly hand the player parallel crafting.
 *   <li><b>Pause, never drop.</b> A finished craft with nowhere to go stays in the plan's buffer and
 *       the whole queue waits.
 * </ul>
 *
 * <p>No Minecraft type appears here, which is what lets the queue's rules be checked by an ordinary
 * unit test. {@link PlayerItems} is the seam.
 */
public final class AssemblerQueue {

    /** A cancel count meaning the whole row. */
    public static final int ALL = Integer.MAX_VALUE;

    private final List<QueuedPlan> entries = new ArrayList<>();
    private boolean blocked;

    public AssemblerQueue() {
    }

    /** Restores a queue from what was persisted, mid-plan and all. */
    public static AssemblerQueue of(List<QueuedPlan> entries) {
        AssemblerQueue queue = new AssemblerQueue();
        for (QueuedPlan entry : entries) {
            queue.entries.add(QueuedPlan.restored(
                    entry.plan(), entry.buffer(), entry.stepIndex(), entry.craftsDone(), entry.progressTicks()));
        }
        return queue;
    }

    /** The queue in order, head first. */
    public List<QueuedPlan> entries() {
        return List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The plan being crafted, if any. */
    public Optional<QueuedPlan> head() {
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.get(0));
    }

    /**
     * Whether the head is paused on a craft it cannot deliver.
     *
     * <p>Derived from the last tick and never persisted: a queue restored into an inventory with room
     * in it must not come back paused because it was paused when the player logged out.
     */
    public boolean isBlocked() {
        return blocked;
    }

    /**
     * Takes the plan's whole raw cost and appends it, or grows the last row when it makes the same
     * thing.
     *
     * <p>Returns false and takes nothing when the player's items do not cover the cost. The Resolver
     * only plans what the items cover, but they can change between resolving and queueing, and this
     * check is what keeps the payment honest.
     */
    public boolean enqueue(CraftingPlan plan, PlayerItems items) {
        if (!take(plan, items)) return false;
        QueuedPlan tail = entries.isEmpty() ? null : entries.getLast();
        if (tail != null && makesTheSameThing(tail.plan(), plan)) {
            entries.set(entries.size() - 1, tail.extendedBy(plan));
        } else {
            entries.add(new QueuedPlan(plan, ItemBag.ofAmounts(plan.rawCost()), 0, 0, 0));
        }
        return true;
    }

    /**
     * Whether a new plan joins the last row rather than starting its own: the same item, by the same
     * final recipe, as Factorio grows the last matching entry of its hand-craft queue.
     *
     * <p>Only the last row, so the order the player queued in is never rearranged. Joining is safe
     * because the steps run front to back against the plan's own buffer, and the new plan was resolved
     * against items the row's cost had already left.
     */
    private static boolean makesTheSameThing(CraftingPlan row, CraftingPlan plan) {
        return row.rootItem().equals(plan.rootItem())
                && !row.steps().isEmpty() && !plan.steps().isEmpty()
                && row.steps().getLast().recipe().equals(plan.steps().getLast().recipe());
    }

    /**
     * One server tick of the head plan.
     *
     * <p>Delivery is attempted before any work is done, which is what makes a pause recover on its own
     * the moment the player frees a slot.
     */
    public void tick(PlayerItems items) {
        if (entries.isEmpty()) {
            blocked = false;
            return;
        }
        QueuedPlan head = entries.get(0);
        if (!deliver(head, items)) {
            blocked = true;
            return;
        }
        blocked = false;
        if (head.finished()) {
            entries.remove(0);
            return;
        }
        head.advanceTick();
        if (head.progressTicks() >= head.currentStep().time()) {
            head.completeCraft();
            if (!deliver(head, items)) {
                blocked = true;
                return;
            }
            if (head.finished()) entries.remove(0);
        }
    }

    /**
     * Cancels a plan wherever it sits, refunding its buffer: the unspent cost and every intermediate
     * already made.
     *
     * <p>The plan is the unit, never an item inside it: cancelling one intermediate out of a Crafting
     * Plan would orphan everything downstream of it (ADR-0001).
     */
    public CancelResult cancel(UUID planId, PlayerItems items) {
        return cancel(planId, ALL, items, (recipe, crafts, id) -> Optional.empty());
    }

    /**
     * Cancels {@code crafts} of a row's final item, or the whole row when that is all of it.
     *
     * <p>Cancelling fewer is a Partial cancel. A row is a resolved plan, and the crafts left over need
     * a plan of their own: how many sticks three torches want is the Resolver's question, not the
     * queue's. So the row is refunded whole and {@code replan} resolves what is left against the
     * refunded items, which reuses the intermediates already made and takes again what the rest costs.
     * The row keeps its id and its place, and the craft under way keeps its progress when the new plan
     * starts on the same recipe. A row whose refund did not all fit is cancelled whole, since the part
     * that did not fit can no longer pay for the rest; a row the Resolver cannot plan again is left
     * exactly as it was.
     */
    public CancelResult cancel(UUID planId, int crafts, PlayerItems items, Replanner replan) {
        if (crafts <= 0) return new CancelResult(false, List.of());
        for (int i = 0; i < entries.size(); i++) {
            QueuedPlan entry = entries.get(i);
            if (!entry.plan().id().equals(planId)) continue;
            List<ItemAmount> notReturned = new ArrayList<>();
            for (ItemAmount held : entry.held()) {
                if (!items.give(held.item(), held.count())) notReturned.add(held);
            }
            entries.remove(i);
            if (i == 0) blocked = false;
            int keep = entry.remainingRootCrafts() - crafts;
            if (keep <= 0 || !notReturned.isEmpty()) {
                return new CancelResult(true, List.copyOf(notReturned));
            }
            CraftStep under = entry.currentStep();
            Optional<CraftingPlan> rest = replan.plan(entry.plan().steps().getLast().recipe(), keep, planId)
                    .filter(plan -> take(plan, items));
            if (rest.isEmpty()) {
                // The rest could not be planned again: put the row back as it was, refund and all.
                for (ItemAmount held : entry.held()) items.take(held.item(), held.count());
                entries.add(i, entry);
                return new CancelResult(false, List.of());
            }
            CraftingPlan plan = rest.get();
            boolean sameCraft = i == 0 && under != null && !plan.steps().isEmpty()
                    && plan.steps().getFirst().recipe().equals(under.recipe());
            entries.add(i, new QueuedPlan(plan, ItemBag.ofAmounts(plan.rawCost()), 0, 0,
                    sameCraft ? entry.progressTicks() : 0));
            return new CancelResult(true, List.of());
        }
        return new CancelResult(false, List.of());
    }

    /**
     * Cancels every row, refunding each one's buffer, and says what would not fit.
     *
     * <p>This is what death does, before the player's items drop: what the plans held follows the
     * normal death rules rather than vanishing with the queue. What did not fit is the caller's to
     * drop with the rest.
     */
    public List<ItemAmount> refundAll(PlayerItems items) {
        List<ItemAmount> notReturned = new ArrayList<>();
        for (QueuedPlan entry : entries) {
            for (ItemAmount held : entry.held()) {
                if (!items.give(held.item(), held.count())) notReturned.add(held);
            }
        }
        entries.clear();
        blocked = false;
        return List.copyOf(notReturned);
    }

    /** Takes a plan's raw cost, or nothing when the player's items do not cover all of it. */
    private static boolean take(CraftingPlan plan, PlayerItems items) {
        for (ItemAmount owed : plan.rawCost()) {
            if (items.count(owed.item()) < owed.count()) return false;
        }
        for (ItemAmount owed : plan.rawCost()) {
            items.take(owed.item(), owed.count());
        }
        return true;
    }

    /** Resolves {@code crafts} of a recipe against the player's items, as a plan carrying {@code id}. */
    @FunctionalInterface
    public interface Replanner {
        Optional<CraftingPlan> plan(String recipe, int crafts, UUID id);
    }

    /**
     * Hands the player everything in the buffer that no remaining step needs, and reports whether all
     * of it went.
     *
     * <p>Computing what is spare from the steps that are left, rather than marking outputs when the
     * plan was resolved, is what makes a recipe that over-produces work for free: two sticks made for a
     * step that wants one leave the spare with the player on the tick it was crafted.
     */
    private boolean deliver(QueuedPlan entry, PlayerItems items) {
        ItemBag needed = new ItemBag();
        List<CraftStep> steps = entry.plan().steps();
        for (int i = entry.stepIndex(); i < steps.size(); i++) {
            CraftStep step = steps.get(i);
            int done = i == entry.stepIndex() ? entry.craftsDone() : 0;
            for (ItemAmount input : step.inputs()) {
                needed.add(input.item(), step.remaining(input.count(), done));
            }
        }
        boolean allDelivered = true;
        for (ItemAmount held : entry.held()) {
            int spare = held.count() - needed.count(held.item());
            if (spare <= 0) continue;
            if (items.give(held.item(), spare)) {
                entry.takeFromBuffer(held.item(), spare);
            } else {
                allDelivered = false;
            }
        }
        return allDelivered;
    }

    /**
     * What a cancel did. {@code notReturned} is what would not fit: the caller's to deal with, since a
     * cancel is the player's own action and dropping at their feet is a fair answer there in a way it
     * never is for a finished craft.
     */
    public record CancelResult(boolean cancelled, List<ItemAmount> notReturned) {
    }
}
