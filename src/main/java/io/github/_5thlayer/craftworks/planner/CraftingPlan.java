// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.List;
import java.util.UUID;

/**
 * A Crafting Plan: the flattened tree, in the order it will be crafted, and the raw cost that pays for
 * all of it.
 *
 * <p>This is what queueing hands the Plan queue, and it is the unit of cancelling (ADR-0001). By
 * the time one exists it is complete: an incomplete resolution never becomes a plan, which is why
 * nothing in the queue asks whether a plan can be finished.
 *
 * <p>{@code rawCost} is what leaves the player's items when the plan is queued: leaves, and any
 * intermediate the player already held.
 */
public record CraftingPlan(UUID id, String rootItem, int amount, List<ItemAmount> rawCost, List<CraftStep> steps) {

    public CraftingPlan {
        if (id == null) throw new IllegalArgumentException("a plan needs an id to be cancelled by");
        if (rootItem == null || rootItem.isBlank()) {
            throw new IllegalArgumentException("a plan needs the item it is for");
        }
        rawCost = List.copyOf(rawCost);
        steps = List.copyOf(steps);
    }

    /** This plan under another id: a Partial cancel's rest keeps the row's, so its cancel still reaches it. */
    public CraftingPlan withId(UUID newId) {
        return new CraftingPlan(newId, rootItem, amount, rawCost, steps);
    }
}
