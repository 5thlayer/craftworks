// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.github._5thlayer.craftworks.planner.TestBags.have;
import static io.github._5thlayer.craftworks.planner.TestBags.stocked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each craft of a Data-carrying recipe asks the craft-finish hook what it made from the keys it consumed, and the
 * plan banks that, item and count as made; an intermediate pays for its own step whatever data it gained (ADR-0022).
 */
class DataCarryingFinishTest {

    private static final String PRISTINE = "iron_sword";
    private static final String NAMED = "iron_sword[name=Biter]";
    private static final String NAMED_RESULT = "diamond_sword[name=Biter]";

    private static AssemblingRecipe upgrade() {
        return new AssemblingRecipe("upgrade", List.of(Ingredient.of("diamond", 1), Ingredient.of(PRISTINE, 1)),
                new ItemAmount("diamond_sword", 1), 2, 0, true, true);
    }

    /** A hook that names the result when a named sword was consumed, as the original recipe would, and counts it twice. */
    private static CraftFinish namesIt(List<List<ItemAmount>> asked) {
        return (recipe, consumed, planned) -> {
            asked.add(consumed);
            boolean named = consumed.stream().anyMatch(input -> input.item().equals(NAMED));
            return named ? new ItemAmount(NAMED_RESULT, 2) : planned;
        };
    }

    private static PlanQueue queued(AssemblingRecipe recipe, int crafts, TestPlayerItems items) {
        Resolver resolver = new Resolver(AssemblingRecipeSet.builder().add(recipe).build(), Set.of()::contains);
        PlanQueue queue = new PlanQueue();
        assertTrue(queue.enqueue(resolver.resolve(recipe.id(), crafts, ItemBag.of(items.contents()))
                .toPlan(UUID.randomUUID(), recipe.result().item(), crafts), items));
        return queue;
    }

    private static void run(PlanQueue queue, TestPlayerItems items, CraftFinish finish) {
        for (int tick = 0; tick < 1000 && !queue.isEmpty(); tick++) queue.tick(items, finish);
        assertTrue(queue.isEmpty(), "the queue should have finished");
    }

    @Test
    void theFinalResultComesFromTheHookCountIncluded() {
        TestPlayerItems items = stocked(have("diamond", 1, NAMED, 1));
        PlanQueue queue = queued(upgrade(), 1, items);
        List<List<ItemAmount>> asked = new ArrayList<>();

        run(queue, items, namesIt(asked));

        assertEquals(Map.of(NAMED_RESULT, 2), items.contents());
        assertEquals(1, asked.size());
        assertEquals(Map.of("diamond", 1, NAMED, 1), ItemBag.ofAmounts(asked.get(0)).asMap());
    }

    @Test
    void craftsOfOneStepBankDifferentResults() {
        TestPlayerItems items = stocked(have("diamond", 2, PRISTINE, 1, NAMED, 1));
        PlanQueue queue = queued(upgrade(), 2, items);
        List<List<ItemAmount>> asked = new ArrayList<>();

        run(queue, items, namesIt(asked));

        assertEquals(Map.of("diamond_sword", 1, NAMED_RESULT, 2), items.contents());
        assertEquals(2, asked.size());
        for (List<ItemAmount> craft : asked) {
            assertEquals(2, ItemBag.ofAmounts(craft).asMap().values().stream().mapToInt(i -> i).sum());
        }
    }

    @Test
    void keysAssignedToLaterCraftsAreNotDeliveredEarly() {
        TestPlayerItems items = stocked(have("diamond", 4, PRISTINE, 2, NAMED, 2));
        PlanQueue queue = queued(upgrade(), 4, items);

        for (int tick = 0; tick < 1000 && !queue.isEmpty(); tick++) {
            queue.tick(items, namesIt(new ArrayList<>()));
            assertFalse(items.contents().containsKey(NAMED), "a named sword a later craft needs was handed back");
        }

        assertEquals(Map.of("diamond_sword", 2, NAMED_RESULT, 4), items.contents());
    }

    @Test
    void anIntermediateWithDataPaysForTheStepItWasMadeFor() {
        AssemblingRecipe toAdvanced = new AssemblingRecipe("to_advanced",
                List.of(Ingredient.of("basic", 1), Ingredient.of("gold", 1)), new ItemAmount("advanced", 1), 2, 0, true, true);
        AssemblingRecipe toElite = new AssemblingRecipe("to_elite",
                List.of(Ingredient.of("advanced", 1), Ingredient.of("gold", 1)), new ItemAmount("elite", 1), 2, 0, true, true);
        Resolver resolver = new Resolver(AssemblingRecipeSet.builder().add(toAdvanced).add(toElite).build(), Set.of()::contains);
        TestPlayerItems items = stocked(have("basic[full=1]", 1, "gold", 2));
        PlanQueue queue = new PlanQueue();
        assertTrue(queue.enqueue(resolver.resolve("to_elite", 1, ItemBag.of(items.contents()))
                .toPlan(UUID.randomUUID(), "elite", 1), items));
        List<List<ItemAmount>> asked = new ArrayList<>();
        CraftFinish keeps = (recipe, consumed, planned) -> {
            asked.add(consumed);
            return new ItemAmount(planned.item() + "[full=1]", planned.count());
        };

        for (int tick = 0; tick < 1000 && !queue.isEmpty(); tick++) {
            queue.tick(items, keeps);
            assertFalse(items.contents().containsKey("advanced[full=1]"), "the intermediate was handed back");
        }

        assertEquals(Map.of("elite[full=1]", 1), items.contents());
        assertEquals(Map.of("advanced[full=1]", 1, "gold", 1), ItemBag.ofAmounts(asked.get(1)).asMap());
    }

    @Test
    void anOrdinaryStepNeverAsksTheHook() {
        AssemblingRecipe plain = new AssemblingRecipe("plain",
                List.of(Ingredient.of("diamond", 1)), new ItemAmount("gem", 1), 2);
        TestPlayerItems items = stocked(have("diamond", 1));
        PlanQueue queue = queued(plain, 1, items);

        run(queue, items, (recipe, consumed, planned) -> {
            throw new AssertionError("asked about " + recipe);
        });

        assertEquals(Map.of("gem", 1), items.contents());
    }

    @Test
    void aDataKeysRemainderIsTheListedKeys() {
        AssemblingRecipe recipe = new AssemblingRecipe("brew",
                List.of(new Ingredient(List.of("water_bucket"), 1, Map.of("water_bucket", "bucket"))),
                new ItemAmount("mud", 1), 2, 0, true, true);
        Resolver resolver = new Resolver(AssemblingRecipeSet.builder().add(recipe).build(), Set.of()::contains);

        Resolver.Resolution resolution = resolver.resolve("brew", 1, have("water_bucket[name=Aqua]", 1));

        assertTrue(resolution.complete());
        assertEquals(Map.of("bucket", 1), ItemBag.ofAmounts(resolution.steps().get(0).remainders()).asMap());
    }
}
