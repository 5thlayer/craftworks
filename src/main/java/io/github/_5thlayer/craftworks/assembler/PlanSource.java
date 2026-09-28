// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import io.github._5thlayer.craftworks.planner.CraftingPlan;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where a Crafting Plan comes from, for a player on a running server.
 *
 * <p>Resolving is server-side because a plan is server truth: it reads the player's inventory and the
 * Lock source, and queueing takes the cost off the back of it. A client-held plan would have to be
 * re-validated when queued, which is exactly the re-validation paying up front was meant to remove.
 */
public interface PlanSource {

    /** The resolver in force. */
    PlanSource ACTIVE = new RuntimePlanSource();

    /** Resolves {@code crafts} of a recipe against what the player has and may use. */
    ResolvedPlan resolve(ServerPlayer player, Identifier recipe, int crafts);

    /**
     * The largest count whose complete plan the inventory covers: Shift's "as many as covered", so it
     * can never queue a plan the inventory then refuses (ADR-0003).
     */
    int largestAffordable(ServerPlayer player, Identifier recipe);

    /**
     * A resolution: what the Crafting Plan shows, and the plan to queue. {@code plan} is null exactly
     * when the plan is incomplete, which is what queueing refuses on.
     */
    record ResolvedPlan(PlanDisplay display, CraftingPlan plan) {

        public boolean complete() {
            return plan != null;
        }
    }
}
