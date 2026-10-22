// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.Set;

import io.github._5thlayer.craftworks.planner.Planning;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which Assembling recipes are Ready for a player: the {@link Planning} asked once per recipe, on a running
 * server (ADR-0012).
 *
 * <p>It holds no rules, as {@link PlayerPlanning} does not: the Planning decides, with the same
 * inventory slots and the same Lock source Fill Recipe uses, so Ready cannot disagree with what a click
 * would queue. Every Assembling recipe is a candidate, not only those whose direct inputs are held,
 * because a recipe reached only through intermediates is Ready too.
 */
public final class ReadyRecipes {

    private ReadyRecipes() {
    }

    /**
     * A pass for {@link ReadyRefresh} to spread over ticks. The inventory is read here, once, so the pass
     * answers for one moment however long it takes.
     *
     * <p>The Lock source is asked once per recipe id and the answer kept for the pass (ADR-0024), as it is
     * for every {@link PlayerPlanning}: a resolve asks about the same recipe from every route that reaches
     * it, and the Lock sources are not cheap. A lock that lifts mid-pass is seen by the next pass; Fill Recipe
     * asks afresh on every request.
     *
     * @param lastReady what the previous pass found Ready, which is resolved first
     */
    static ReadyRefresh.Pass begin(ServerPlayer player, Set<String> lastReady) {
        PlayerPlanning planning = PlayerPlanning.of(player);
        return new ReadyPass(planning.planning(), ReadyPass.ordered(planning.recipeIds(), lastReady));
    }

    /** The whole set in one go, ignoring the budget: for a test, or a caller that can afford it. */
    public static Set<String> of(ServerPlayer player) {
        ReadyRefresh.Pass pass = begin(player, Set.of());
        while (!pass.done()) pass.resolveNext();
        return pass.ready();
    }
}
