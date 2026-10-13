// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.List;

/**
 * What a Crafting Plan shows of its final result before it is queued (ADR-0022).
 *
 * <p>A Data-carrying recipe makes what the mod's original recipe assembles from the stacks its crafts take, so
 * the listed result is only a guess. The plan is run here on a copy of its own cost, craft by craft, through the
 * same {@link CraftFinish} the queue ticks with: nothing is taken and nothing is delivered. Intermediates stay as
 * listed, and the plan still counts on the listed result.
 */
public final class PlanPreview {

    private PlanPreview() {
    }

    /**
     * {@code toCraft} with the final result's listed row replaced by one row per distinct predicted result;
     * {@code toCraft} unchanged when the final step is not a Data-carrying recipe's.
     */
    public static List<ItemAmount> toCraft(CraftingPlan plan, List<ItemAmount> toCraft, CraftFinish finish) {
        if (plan.steps().isEmpty() || !plan.steps().getLast().dataCarrying()) return toCraft;
        CraftStep last = plan.steps().getLast();
        ItemAmount listed = last.outputs().get(0);
        ItemBag predicted = new ItemBag();
        CraftFinish recording = (recipe, consumed, planned) -> {
            ItemAmount made = finish.result(recipe, consumed, planned);
            predicted.add(made.item(), made.count());
            return made;
        };
        QueuedPlan run = new QueuedPlan(plan, ItemBag.ofAmounts(plan.rawCost()), 0, 0, 0);
        while (!run.finished()) {
            run.completeCraft(run.currentStep() == last ? recording : finish);
        }
        ItemBag shown = ItemBag.ofAmounts(toCraft);
        shown.remove(listed.item(), listed.count());
        for (ItemAmount row : predicted.amounts()) shown.add(row.item(), row.count());
        return shown.amounts();
    }
}
