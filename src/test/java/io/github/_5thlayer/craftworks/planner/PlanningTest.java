// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import static io.github._5thlayer.craftworks.planner.TestBags.have;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/**
 * A Planning resolves each question once and keeps its two ways in, {@code plan} and {@code ready}, apart:
 * the first names everything Missing and Locked, the second is the Ready pass's cheap yes or no.
 */
class PlanningTest {

    private static final AssemblingRecipeSet CHAIN = AssemblingRecipeSet.builder()
            .add(BigRecipes.recipe("plate", "plate", Ingredient.of("ore", 1)))
            .add(BigRecipes.recipe("gear", "gear", Ingredient.of("plate", 2)))
            .add(BigRecipes.recipe("stick", "stick", Ingredient.of("plank", 2)))
            .add(BigRecipes.recipe("plank", "plank", Ingredient.of("log", 1)))
            .add(BigRecipes.recipe("torch", "torch", Ingredient.of("stick", 1), Ingredient.of("coal", 1)))
            .build();

    private static Resolver resolver() {
        return new Resolver(CHAIN, id -> false);
    }

    @Test
    void theCraftingPlansViewAndItsCeilingResolveOneCraftOnce() {
        ItemBag stock = have("ore", 20);
        Resolver ceilingOnly = resolver();
        ceilingOnly.planning(stock).largestAffordable("gear");

        Resolver view = resolver();
        Planning planning = view.planning(stock);
        planning.plan("gear", 1);
        planning.largestAffordable("gear");

        assertTrue(ceilingOnly.applicationsTried() > 0);
        assertEquals(ceilingOnly.applicationsTried(), view.applicationsTried(),
                "the view's one craft should be the ceiling's first resolve, not another");
    }

    @Test
    void queueingTheCeilingCostsNoFurtherResolve() {
        Resolver resolver = resolver();
        Planning planning = resolver.planning(have("ore", 20));
        int all = planning.largestAffordable("gear");
        assertEquals(10, all);
        long before = resolver.applicationsTried();

        assertTrue(planning.plan("gear", all).complete());
        assertEquals(before, resolver.applicationsTried());
    }

    @Test
    void thePlanAskedTwiceIsTheSameResolution() {
        Planning planning = resolver().planning(have("ore", 4));
        assertSame(planning.plan("gear", 2), planning.plan("gear", 2));
    }

    @Test
    void theStockIsCopiedWhenThePlanningIsMade() {
        ItemBag stock = have("ore", 4);
        Planning planning = resolver().planning(stock);
        stock.add("ore", 100);
        assertEquals(2, planning.largestAffordable("gear"));
    }

    @Test
    void readyAgreesWithACompletePlanForEveryRecipeOfTheBigSet() {
        for (Predicate<String> locked : List.<Predicate<String>>of(id -> false, id -> id.hashCode() % 5 == 0)) {
            for (boolean banners : new boolean[] {false, true}) {
                AssemblingRecipeSet recipes = BigRecipes.build(3, 4, 2, banners);
                ItemBag stock = BigRecipes.stock(36, 0, 2);
                Resolver resolver = new Resolver(recipes, locked);
                // One Planning answers Ready for the whole set, as a pass does, so the memo carries across recipes.
                Planning pass = resolver.planning(stock);
                for (String id : new ArrayList<>(recipes.ids())) {
                    assertEquals(resolver.planning(stock).plan(id, 1).complete(), pass.ready(id),
                            "ready and a complete plan disagree on " + id);
                }
            }
        }
    }

    @Test
    void aPlanStillNamesEverythingMissingAfterReadyProbedTheSamePlanning() {
        // One log is reached and makes one plank, but a stick takes two: only a probe sees that, and
        // only Ready may rely on it (ADR-0024).
        ItemBag stock = have("log", 1, "coal", 1);
        Planning planning = resolver().planning(stock);

        assertFalse(planning.ready("torch"));
        assertFalse(planning.ready("stick"));

        Resolver.Resolution after = planning.plan("torch", 1);
        Resolver.Resolution fresh = resolver().planning(stock).plan("torch", 1);
        assertFalse(after.missing().isEmpty(), "the torch is not complete");
        assertEquals(fresh, after, "ready() leaked into plan()");
        assertTrue(after.missing().stream().anyMatch(missing -> missing.item().equals("log")),
                "the log the second plank needs should be named: " + after.missing());
    }

    @Test
    void readyDoesNotFillThePlanCache() {
        Resolver resolver = resolver();
        Planning planning = resolver.planning(have("ore", 4));
        assertTrue(planning.ready("gear"));
        long afterReady = resolver.applicationsTried();
        assertTrue(planning.plan("gear", 1).complete());
        assertTrue(resolver.applicationsTried() > afterReady, "plan() should resolve for itself");
    }
}
