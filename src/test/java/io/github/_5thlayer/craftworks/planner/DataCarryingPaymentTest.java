// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.github._5thlayer.craftworks.planner.TestBags.asMap;
import static io.github._5thlayer.craftworks.planner.TestBags.have;
import static io.github._5thlayer.craftworks.planner.TestBags.stocked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Data-carrying recipe's ingredients accept an item carrying data as well as a Pristine one, Pristine
 * first, then inventory order; every other recipe keeps ADR-0002's exact match (ADR-0022).
 */
class DataCarryingPaymentTest {

    private static final String PRISTINE = "iron_sword";
    private static final String NAMED = "iron_sword[name=Biter]";
    private static final String OTHER = "iron_sword[name=Fang]";

    private static AssemblingRecipe upgrade(boolean dataCarrying) {
        return new AssemblingRecipe("upgrade", List.of(Ingredient.of("diamond", 1), Ingredient.of(PRISTINE, 1)),
                new ItemAmount("diamond_sword", 1), 10, 0, true, dataCarrying);
    }

    private static Resolver resolverOf(AssemblingRecipe recipe) {
        return new Resolver(AssemblingRecipeSet.builder().add(recipe).build(), Set.of()::contains);
    }

    private static Resolver.Resolution plan(boolean dataCarrying, ItemBag inventory) {
        return resolverOf(upgrade(dataCarrying)).resolve("upgrade", 1, inventory);
    }

    @Test
    void aDataCarryingIngredientTakesAKeyWithData() {
        Resolver.Resolution resolution = plan(true, have("diamond", 1, NAMED, 1));

        assertTrue(resolution.complete());
        assertEquals(Map.of("diamond", 1, NAMED, 1), asMap(resolution.rawCost()));
        assertEquals(Map.of("diamond", 1, NAMED, 1), asMap(resolution.steps().get(0).inputs()));
    }

    @Test
    void anOrdinaryRecipeRefusesAKeyWithData() {
        Resolver.Resolution resolution = plan(false, have("diamond", 1, NAMED, 1));

        assertFalse(resolution.complete());
        assertEquals(Map.of(PRISTINE, 1), asMap(resolution.missing()));
    }

    @Test
    void thePristineItemPaysBeforeOneWithData() {
        Resolver.Resolution resolution = plan(true, have("diamond", 1, NAMED, 1, PRISTINE, 1));

        assertEquals(Map.of("diamond", 1, PRISTINE, 1), asMap(resolution.rawCost()));
    }

    @Test
    void itemsWithDataPayInInventoryOrder() {
        Resolver.Resolution resolution = plan(true, have("diamond", 1, OTHER, 1, NAMED, 1));

        assertEquals(Map.of("diamond", 1, OTHER, 1), asMap(resolution.rawCost()));
    }

    @Test
    void aDifferentItemWithDataDoesNotPay() {
        assertFalse(plan(true, have("diamond", 1, "stone_sword[name=Biter]", 1)).complete());
    }

    @Test
    void severalCraftsTakeTheirOwnStacksPristineFirst() {
        Resolver.Resolution resolution = resolverOf(upgrade(true))
                .resolve("upgrade", 3, have("diamond", 3, NAMED, 1, PRISTINE, 1, OTHER, 1));

        assertEquals(Map.of("diamond", 3, PRISTINE, 1, NAMED, 1, OTHER, 1), asMap(resolution.rawCost()));
    }

    @Test
    void largestAffordableCountsItemsWithData() {
        ItemBag inventory = have("diamond", 5, NAMED, 1, OTHER, 1);

        assertEquals(2, resolverOf(upgrade(true)).largestAffordable("upgrade", inventory));
        assertEquals(0, resolverOf(upgrade(false)).largestAffordable("upgrade", inventory));
    }

    @Test
    void queueingTakesTheExactKeysAndACancelReturnsThemExactly() {
        TestPlayerItems items = stocked(have("diamond", 2, PRISTINE, 1, NAMED, 2));
        Resolver.Resolution resolution = resolverOf(upgrade(true)).resolve("upgrade", 2, bagOf(items));
        UUID id = UUID.randomUUID();
        PlanQueue queue = new PlanQueue();

        assertTrue(queue.enqueue(resolution.toPlan(id, "diamond_sword", 2), items));
        assertEquals(Map.of(NAMED, 1), items.contents());

        assertTrue(queue.cancel(id, items).cancelled());
        assertEquals(Map.of("diamond", 2, PRISTINE, 1, NAMED, 2), items.contents());
    }

    @Test
    void aPartialCancelReplansAgainstTheRefundPristineFirst() {
        TestPlayerItems items = stocked(have("diamond", 2, NAMED, 1, PRISTINE, 1));
        Resolver resolver = resolverOf(upgrade(true));
        UUID id = UUID.randomUUID();
        PlanQueue queue = new PlanQueue();
        assertTrue(queue.enqueue(resolver.resolve("upgrade", 2, bagOf(items)).toPlan(id, "diamond_sword", 2), items));
        assertTrue(items.contents().isEmpty());

        PlanQueue.CancelResult result = queue.cancel(id, 1, items, (recipe, crafts, planId) ->
                Optional.of(resolver.resolve(recipe, crafts, bagOf(items)).toPlan(planId, "diamond_sword", crafts)));

        // The refund held a Pristine and a named sword; the one craft left takes the Pristine one.
        assertTrue(result.cancelled());
        assertEquals(Map.of("diamond", 1, NAMED, 1), items.contents());
        assertEquals(Map.of("diamond", 1, PRISTINE, 1), queue.entries().get(0).buffer());
    }

    @Test
    void aQueueHoldingKeysWithDataRoundTripsThroughTheCodecs() {
        TestPlayerItems items = stocked(have("diamond", 1, NAMED, 1));
        PlanQueue queue = new PlanQueue();
        assertTrue(queue.enqueue(plan(true, bagOf(items)).toPlan(UUID.randomUUID(), "diamond_sword", 1), items));

        JsonElement json = AssemblerCodecs.QUEUE.encodeStart(JsonOps.INSTANCE, queue).getOrThrow();
        PlanQueue back = AssemblerCodecs.QUEUE.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(queue.entries(), back.entries());
        assertEquals(Map.of("diamond", 1, NAMED, 1), back.entries().get(0).buffer());
    }

    @Test
    void aQueuedPlanRunsToTheEndOnItsDataKeys() {
        TestPlayerItems items = stocked(have("diamond", 1, NAMED, 1));
        PlanQueue queue = new PlanQueue();
        assertTrue(queue.enqueue(plan(true, bagOf(items)).toPlan(UUID.randomUUID(), "diamond_sword", 1), items));

        for (int tick = 0; tick < 100 && !queue.isEmpty(); tick++) queue.tick(items);

        assertEquals(Map.of("diamond_sword", 1), items.contents());
    }

    private static ItemBag bagOf(TestPlayerItems items) {
        return ItemBag.of(items.contents());
    }

    @Test
    void aHeldItemWithDataIsSpentBeforeThePlanCraftsAFreshPristineOne() {
        AssemblingRecipe forge = new AssemblingRecipe("forge", List.of(Ingredient.of("iron", 1)),
                new ItemAmount(PRISTINE, 1), 10);
        Resolver resolver = new Resolver(AssemblingRecipeSet.builder().add(upgrade(true)).add(forge).build(), Set.of()::contains);

        Resolver.Resolution resolution = resolver.resolve("upgrade", 1, have("diamond", 1, NAMED, 1, "iron", 1));

        assertTrue(resolution.complete());
        assertEquals(Map.of("diamond", 1, NAMED, 1), asMap(resolution.rawCost()));
        assertEquals(1, resolution.steps().size());
    }

    @Test
    void itemsWithDataPayInInventoryOrderAcrossSeveralKeys() {
        Resolver.Resolution resolution = resolverOf(upgrade(true))
                .resolve("upgrade", 3, have("diamond", 3, OTHER, 1, NAMED, 2, "iron_sword[name=Claw]", 1));

        assertEquals(Map.of("diamond", 3, OTHER, 1, NAMED, 2), asMap(resolution.rawCost()));
    }

    @Test
    void aQueueOfSeveralCraftsHoldingKeysWithDataRoundTripsWithItsPerCraftLayout() {
        TestPlayerItems items = stocked(have("diamond", 3, NAMED, 1, OTHER, 1, PRISTINE, 1));
        PlanQueue queue = new PlanQueue();
        assertTrue(queue.enqueue(resolverOf(upgrade(true)).resolve("upgrade", 3, bagOf(items))
                .toPlan(UUID.randomUUID(), "diamond_sword", 3), items));
        assertFalse(queue.entries().get(0).plan().steps().get(0).perCraft().isEmpty());

        JsonElement json = AssemblerCodecs.QUEUE.encodeStart(JsonOps.INSTANCE, queue).getOrThrow();
        PlanQueue back = AssemblerCodecs.QUEUE.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(queue.entries(), back.entries());
        assertEquals(queue.entries().get(0).plan().steps().get(0).perCraft(),
                back.entries().get(0).plan().steps().get(0).perCraft());
    }

    @Test
    void aPartialCancelOfSeveralCraftsRefundsTheExactKeysAndReplans() {
        TestPlayerItems items = stocked(have("diamond", 3, NAMED, 1, OTHER, 1, PRISTINE, 1));
        Resolver resolver = resolverOf(upgrade(true));
        UUID id = UUID.randomUUID();
        PlanQueue queue = new PlanQueue();
        assertTrue(queue.enqueue(resolver.resolve("upgrade", 3, bagOf(items)).toPlan(id, "diamond_sword", 3), items));

        PlanQueue.CancelResult result = queue.cancel(id, 1, items, (recipe, crafts, planId) ->
                Optional.of(resolver.resolve(recipe, crafts, bagOf(items)).toPlan(planId, "diamond_sword", crafts)));

        assertTrue(result.cancelled());
        assertEquals(2, queue.entries().get(0).plan().amount());
        int keysHeld = items.contents().values().stream().mapToInt(i -> i).sum()
                + queue.entries().get(0).buffer().values().stream().mapToInt(i -> i).sum();
        assertEquals(6, keysHeld, "three diamonds and three swords, none lost or invented");
    }
}
