// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.github._5thlayer.craftworks.planner.TestBags.have;
import static io.github._5thlayer.craftworks.planner.TestBags.recipe;
import static io.github._5thlayer.craftworks.planner.TestBags.stocked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An ingredient's own remainder, the empty bucket after the milk (ADR-0006): it returns to the player's
 * items when its step completes, and a plan never counts on it, so the whole cost is still paid up
 * front.
 */
class RemainderTest {

    private static final Ingredient MILK = new Ingredient(List.of("milk_bucket"), 3, Map.of("milk_bucket", "bucket"));

    private static final AssemblingRecipeSet RECIPES = AssemblingRecipeSet.builder()
            .add(recipe("cake", "cake", 1, 10, MILK, Ingredient.of("sugar", 2)))
            .build();

    private static final Resolver RESOLVER = new Resolver(RECIPES, Set.of()::contains);

    private static void run(AssemblerQueue queue, TestPlayerItems items, int ticks) {
        for (int tick = 0; tick < ticks; tick++) queue.tick(items);
    }

    @Test
    void theRemainderReachesThePlayerWhenItsStepCompletes() {
        ItemBag inventory = have("milk_bucket", 3, "sugar", 2);
        TestPlayerItems items = stocked(inventory);
        AssemblerQueue queue = new AssemblerQueue();
        assertTrue(queue.enqueue(RESOLVER.resolve("cake", 1, inventory).toPlan(UUID.randomUUID()), items));

        run(queue, items, 9);
        assertEquals(0, items.count("bucket"), "nothing comes back before the step completes");

        queue.tick(items);
        assertEquals(1, items.count("cake"));
        assertEquals(3, items.count("bucket"));
        assertEquals(0, items.count("milk_bucket"));
    }

    @Test
    void eachCraftOfABatchReturnsItsOwnRemainders() {
        ItemBag inventory = have("milk_bucket", 6, "sugar", 4);
        TestPlayerItems items = stocked(inventory);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(RESOLVER.resolve("cake", 2, inventory).toPlan(UUID.randomUUID()), items);

        run(queue, items, 10);
        assertEquals(3, items.count("bucket"), "the first cake's buckets, not the batch's");

        run(queue, items, 10);
        assertEquals(6, items.count("bucket"));
        assertEquals(2, items.count("cake"));
    }

    @Test
    void aPlanDoesNotCountOnARemainder() {
        // The cake would leave three buckets, but the party's bucket has to be paid when the plan is
        // queued, so without one it is Missing.
        AssemblingRecipeSet recipes = AssemblingRecipeSet.builder()
                .add(recipe("cake", "cake", 1, 10, MILK, Ingredient.of("sugar", 2)))
                .add(recipe("party", "party", 1, 10, Ingredient.of("cake", 1), Ingredient.of("bucket", 1)))
                .build();

        Resolver.Resolution resolution = new Resolver(recipes, Set.of()::contains)
                .resolve("party", 1, have("milk_bucket", 3, "sugar", 2));

        assertEquals(Map.of("bucket", 1), TestBags.asMap(resolution.missing()));
    }

    @Test
    void aRemainderWithNoRoomPausesTheQueueAndArrivesOnceThereIs() {
        // Two slots, both freed by the cost; stone fills one while the cake is made, so the cake fits
        // and its buckets do not.
        ItemBag inventory = have("milk_bucket", 3, "sugar", 2);
        TestPlayerItems items = new TestPlayerItems(2, 64).with("milk_bucket", 3).with("sugar", 2);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(RESOLVER.resolve("cake", 1, inventory).toPlan(UUID.randomUUID()), items);
        items.give("stone", 64);

        run(queue, items, 10);
        assertEquals(1, items.count("cake"));
        assertEquals(0, items.count("bucket"));
        assertTrue(queue.isBlocked(), "the buckets wait with the plan rather than being dropped");

        items.take("stone", 64);
        queue.tick(items);
        assertEquals(3, items.count("bucket"));
        assertTrue(queue.isEmpty());
    }
}
