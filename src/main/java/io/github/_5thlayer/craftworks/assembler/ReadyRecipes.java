// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import io.github._5thlayer.craftworks.planner.AssemblingRecipeSet;
import io.github._5thlayer.craftworks.planner.Resolver;
import io.github._5thlayer.craftworks.recipe.RuntimeAssemblingRecipes;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which Assembling recipes are Ready for a player: the Resolver run once per recipe, on a running server
 * (ADR-0012).
 *
 * <p>It holds no rules, as {@link RuntimePlanSource} does not: the Resolver decides, with the same
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
     * <p>The Lock source is asked once per recipe id and the answer kept for the pass (ADR-0024): a
     * resolve asks about the same recipe from every route that reaches it, and the Lock sources are not
     * cheap. A lock that lifts mid-pass is seen by the next pass. Fill Recipe asks live, as it always did.
     *
     * @param lastReady what the previous pass found Ready, which is resolved first
     */
    static ReadyRefresh.Pass begin(ServerPlayer player, Set<String> lastReady) {
        AssemblingRecipeSet recipes = RuntimeAssemblingRecipes.recipes(player.level());
        Predicate<String> live = RuntimePlanSource.lockedFor(player);
        Map<String, Boolean> answers = new HashMap<>();
        return new ReadyPass(new Resolver(recipes, id -> answers.computeIfAbsent(id, live::test)),
                RuntimePlanSource.inventoryOf(player), ReadyPass.ordered(recipes.ids(), lastReady));
    }

    /** The whole set in one go, ignoring the budget: for a test, or a caller that can afford it. */
    public static Set<String> of(ServerPlayer player) {
        ReadyRefresh.Pass pass = begin(player, Set.of());
        while (!pass.done()) pass.resolveNext();
        return pass.ready();
    }
}
