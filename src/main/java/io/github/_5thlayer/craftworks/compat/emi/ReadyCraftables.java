// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.runtime.EmiFavorite;
import io.github._5thlayer.craftworks.assembler.ReadyRecipeIds;
import net.minecraft.resources.Identifier;

/**
 * Widens EMI's Craftables to every Ready Assembling recipe (#17, ADR-0012).
 *
 * <p>EMI builds the list from the recipes that take an item the player holds, so a recipe reached only
 * through intermediates (sticks from a stack of logs) is never a candidate. Ready is per recipe, so each
 * missing one is added as EMI's own {@code Craftable}, after the handler has agreed to it: the same
 * question EMI asks of the rest, answered from the same Ready set.
 */
public final class ReadyCraftables {

    private ReadyCraftables() {
    }

    /**
     * EMI's list with the Ready recipes it left out added, in EMI's order: by the result's place in the
     * index, then by amount, the sort EMI applies. What EMI listed keeps its order among itself.
     *
     * <p>A new list, never the argument: EMI hands back {@code List.of()} when it has no predicate, and
     * an immutable list cannot take an addition. The argument itself is returned when nothing is added.
     *
     * @param predicate EMI's craftable check for the open screen, or null when the screen has no handler,
     *     which is when EMI lists nothing and neither do we
     */
    public static List<EmiIngredient> widen(List<EmiIngredient> listed, Predicate<EmiRecipe> predicate) {
        if (predicate == null || ReadyRecipeIds.all().isEmpty()) return listed;
        Set<String> already = new HashSet<>();
        for (EmiIngredient entry : listed) {
            if (entry instanceof EmiFavorite favorite && favorite.getRecipe() != null
                    && AssemblingEmiRecipe.recipeIdOf(favorite.getRecipe()) != null) {
                already.add(AssemblingEmiRecipe.recipeIdOf(favorite.getRecipe()).toString());
            }
        }
        List<EmiIngredient> widened = new ArrayList<>(listed);
        for (String id : ReadyRecipeIds.all()) {
            if (already.contains(id)) continue;
            Identifier parsed = Identifier.tryParse(id);
            EmiRecipe recipe = parsed == null ? null : EmiApi.getRecipeManager().getRecipe(parsed);
            if (recipe == null || recipe.hideCraftable() || recipe.getOutputs().isEmpty() || !predicate.test(recipe)) continue;
            widened.add(new EmiFavorite.Craftable(recipe));
        }
        if (widened.size() == listed.size()) return listed;
        widened.sort((a, b) -> {
            int byIndex = Integer.compare(indexOf(a), indexOf(b));
            return byIndex != 0 ? byIndex : Long.compare(a.getAmount(), b.getAmount());
        });
        return widened;
    }

    /** Where the entry's result sits in EMI's index: the stack a Craftable wraps, as EMI's own sort reads it. */
    private static int indexOf(EmiIngredient entry) {
        return EmiStackList.getIndex(entry instanceof EmiFavorite favorite ? favorite.getStack() : entry);
    }
}
