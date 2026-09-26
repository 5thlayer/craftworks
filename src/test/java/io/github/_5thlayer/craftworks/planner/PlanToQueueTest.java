// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.github._5thlayer.craftworks.planner.TestBags.have;
import static io.github._5thlayer.craftworks.planner.TestBags.recipe;
import static io.github._5thlayer.craftworks.planner.TestBags.stocked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam between the Resolver and the Assembler queue: a plan the Resolver calls complete must be
 * one the queue can actually run to the end.
 *
 * <p>Asserted here because neither side can assert it alone, and because the failure is invisible from
 * either. The queue throws when a plan's buffer cannot feed a craft, which is the right answer to a
 * Resolver bug and a terrible thing to discover from a crash report: the cost would already have been
 * taken and the player's items would be inside a plan that cannot finish.
 */
class PlanToQueueTest {

    private static final AssemblingRecipeSet RECIPES = AssemblingRecipeSet.builder()
            .add(recipe("cable", "cable", 2, 5, Ingredient.of("copper", 1)))
            .add(recipe("circuit", "circuit", 1, 10, Ingredient.of("cable", 3), Ingredient.of("plate", 1)))
            .add(recipe("inserter", "inserter", 1, 10,
                    Ingredient.of("circuit", 1), Ingredient.of("gear", 1), Ingredient.of("plate", 1)))
            .add(recipe("gear", "gear", 1, 5, Ingredient.of("plate", 2)))
            .build();

    private static final Resolver RESOLVER = new Resolver(RECIPES, Set.of()::contains);

    /** Runs the queue to the end, and says how many ticks it took. Fails loudly if it never finishes. */
    private static int runToCompletion(AssemblerQueue queue, TestPlayerItems items) {
        for (int tick = 0; tick < 100_000; tick++) {
            if (queue.isEmpty()) return tick;
            queue.tick(items);
        }
        throw new AssertionError("the queue never emptied");
    }

    @Test
    void aThreeDeepChainResolvesAndThenRunsToTheEnd() {
        ItemBag inventory = have("copper", 2, "plate", 4);
        Resolver.Resolution resolution = RESOLVER.resolve("inserter", 1, inventory);
        assertTrue(resolution.complete());

        TestPlayerItems items = stocked(inventory);
        AssemblerQueue queue = new AssemblerQueue();
        assertTrue(queue.enqueue(resolution.toPlan(UUID.randomUUID(), "inserter", 1), items));

        runToCompletion(queue, items);

        assertEquals(1, items.count("inserter"));
        // The cable recipe makes two and the circuit wants three, so the fourth is spare, and it
        // reaches the player rather than vanishing with the plan.
        assertEquals(1, items.count("cable"));
        assertEquals(0, items.count("copper"));
        assertEquals(0, items.count("plate"));
    }

    @Test
    void everyStepOfEveryAffordablePlanIsFedByWhatCameBeforeIt() {
        // largestAffordable is the count the Crafting Plan's `all` puts in front of the player, so it is
        // the count most likely to sit exactly on the edge of the cost. Walking every one of them is
        // what shows the Resolver's rounding never leaves a step short.
        ItemBag inventory = have("copper", 17, "plate", 40);

        int all = RESOLVER.largestAffordable("inserter", inventory);
        assertTrue(all > 1, "the fixture should afford several, got " + all);

        for (int amount = 1; amount <= all; amount++) {
            Resolver.Resolution resolution = RESOLVER.resolve("inserter", amount, inventory);
            assertTrue(resolution.complete(), "plan for " + amount + " should be complete");

            TestPlayerItems items = stocked(inventory);
            AssemblerQueue queue = new AssemblerQueue();
            assertTrue(queue.enqueue(resolution.toPlan(UUID.randomUUID(), "inserter", amount), items));

            runToCompletion(queue, items);

            assertEquals(amount, items.count("inserter"), "plan for " + amount + " delivered short");
        }
    }

    @Test
    void aPlanCancelledPartWayThroughGivesBackEverythingItHeld() {
        ItemBag inventory = have("copper", 2, "plate", 4);
        UUID id = UUID.randomUUID();
        TestPlayerItems items = stocked(inventory);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(RESOLVER.resolve("inserter", 1, inventory).toPlan(id, "inserter", 1), items);
        for (int tick = 0; tick < 12; tick++) queue.tick(items);

        assertTrue(queue.cancel(id, items).cancelled());

        // Both cable crafts are done and the circuit is under way: the copper came back as the four
        // cables it became, and the plates the plan had not yet spent came back as plates.
        assertEquals(0, items.count("copper"));
        assertEquals(4, items.count("cable"));
        assertEquals(4, items.count("plate"));
        assertEquals(0, items.count("inserter"));
    }

    @Test
    void aPartialCancelReplannedByTheResolverStillRunsToTheEnd() {
        ItemBag inventory = have("copper", 6, "plate", 12);
        UUID id = UUID.randomUUID();
        TestPlayerItems items = stocked(inventory);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(RESOLVER.resolve("inserter", 3, inventory).toPlan(id, "inserter", 3), items);
        for (int tick = 0; tick < 12; tick++) queue.tick(items);

        AssemblerQueue.Replanner replan = (recipe, crafts, planId) -> {
            Resolver.Resolution rest = RESOLVER.resolve(recipe, crafts, inventoryOf(items));
            return rest.complete() ? Optional.of(rest.toPlan(planId, "inserter", crafts)) : Optional.empty();
        };
        assertTrue(queue.cancel(id, 1, items, replan).cancelled());
        assertEquals(id, queue.entries().get(0).plan().id(), "a Partial cancel keeps the row's id");

        runToCompletion(queue, items);

        assertEquals(2, items.count("inserter"));
    }

    private static ItemBag inventoryOf(TestPlayerItems items) {
        return ItemBag.of(items.contents());
    }
}
