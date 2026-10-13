// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.github._5thlayer.craftworks.planner.TestBags.have;
import static io.github._5thlayer.craftworks.planner.TestBags.stocked;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Before queueing, a Data-carrying recipe's final result is predicted by the same craft-finish hook the queue
 * uses, from the keys the plan would take, a row per distinct result (ADR-0022).
 */
class PlanPreviewTest {

    private static final String PRISTINE = "iron_sword";
    private static final String NAMED = "iron_sword[name=Biter]";
    private static final String NAMED_RESULT = "diamond_sword[name=Biter]";

    private static final AssemblingRecipe UPGRADE = new AssemblingRecipe("upgrade",
            List.of(Ingredient.of("diamond", 1), Ingredient.of(PRISTINE, 1)),
            new ItemAmount("diamond_sword", 1), 2, 0, true, true);

    private static final CraftFinish NAMES_IT = (recipe, consumed, planned) ->
            consumed.stream().anyMatch(input -> input.item().equals(NAMED))
                    ? new ItemAmount(NAMED_RESULT, planned.count()) : planned;

    private static Resolver.Resolution resolve(AssemblingRecipe recipe, int crafts, Map<String, Integer> stock) {
        Resolver resolver = new Resolver(AssemblingRecipeSet.builder().add(recipe).build(), Set.of()::contains);
        return resolver.resolve(recipe.id(), crafts, ItemBag.of(stock));
    }

    private static List<ItemAmount> predicted(Resolver.Resolution resolution, CraftFinish finish) {
        return PlanPreview.toCraft(resolution.toPlan(UUID.randomUUID(), "diamond_sword", 1), resolution.toCraft(), finish);
    }

    @Test
    void aNamedSwordPredictsANamedResult() {
        Resolver.Resolution resolution = resolve(UPGRADE, 1, stocked(have("diamond", 1, NAMED, 1)).contents());

        assertEquals(List.of(new ItemAmount(NAMED_RESULT, 1)), predicted(resolution, NAMES_IT));
    }

    @Test
    void craftsTakingDifferentStacksPredictDistinctRows() {
        Resolver.Resolution resolution = resolve(UPGRADE, 2,
                stocked(have("diamond", 2, PRISTINE, 1, NAMED, 1)).contents());

        List<ItemAmount> rows = predicted(resolution, NAMES_IT);

        assertEquals(2, rows.size());
        assertEquals(Map.of("diamond_sword", 1, NAMED_RESULT, 1), ItemBag.ofAmounts(rows).asMap());
    }

    @Test
    void anOrdinaryRecipeIsShownAsListedAndNeverAsksTheHook() {
        AssemblingRecipe plain = new AssemblingRecipe("plain",
                List.of(Ingredient.of("diamond", 1)), new ItemAmount("gem", 1), 2);
        Resolver.Resolution resolution = resolve(plain, 1, stocked(have("diamond", 1)).contents());

        assertEquals(resolution.toCraft(), PlanPreview.toCraft(
                resolution.toPlan(UUID.randomUUID(), "gem", 1), resolution.toCraft(), (recipe, consumed, planned) -> {
                    throw new AssertionError("asked about " + recipe);
                }));
    }
}
