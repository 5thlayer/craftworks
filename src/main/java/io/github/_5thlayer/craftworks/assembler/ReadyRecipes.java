// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
     * A pass for {@link ReadyRefresh} to spread over ticks. The inventory and the Lock source are read
     * here, once, so the pass answers for one moment however long it takes.
     */
    static ReadyRefresh.Pass begin(ServerPlayer player) {
        AssemblingRecipeSet recipes = RuntimeAssemblingRecipes.recipes(player.level());
        List<String> candidates = new ArrayList<>(recipes.ids());
        Collections.sort(candidates);
        return new ReadyPass(new Resolver(recipes, RuntimePlanSource.lockedFor(player)),
                RuntimePlanSource.inventoryOf(player), candidates);
    }

    /** The whole set in one go, ignoring the budget: for a test, or a caller that can afford it. */
    public static Set<String> of(ServerPlayer player) {
        ReadyRefresh.Pass pass = begin(player);
        while (!pass.done()) pass.resolveNext();
        return pass.ready();
    }
}
