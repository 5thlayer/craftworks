// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github._5thlayer.craftworks.planner.TestBags.asMap;
import static io.github._5thlayer.craftworks.planner.TestBags.have;
import static io.github._5thlayer.craftworks.planner.TestBags.recipe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Resolver's rules (ADR-0001): chain-crafting, what counts as raw cost, and what a Crafting Plan
 * reports as Missing and Locked.
 *
 * <p>Items are strings and recipes a hand-built set, which is the whole reason the recursion is
 * checkable without a game.
 */
class ResolverTest {

    /** gear = 2 plate; circuit = 3 cable + 1 plate; cable = 1 copper, making 2. */
    private static AssemblingRecipeSet recipes() {
        return AssemblingRecipeSet.builder()
                .add(recipe("gear", "gear", 1, 10, Ingredient.of("plate", 2)))
                .add(recipe("cable", "cable", 2, 10, Ingredient.of("copper", 1)))
                .add(recipe("circuit", "circuit", 1, 20, Ingredient.of("cable", 3), Ingredient.of("plate", 1)))
                .build();
    }

    private static Resolver resolver() {
        return new Resolver(recipes(), Set.of()::contains);
    }

    @Test
    void aRecipeWhoseIngredientsAreOnHandIsOneStep() {
        Resolver.Resolution resolution = resolver().resolve("gear", 1, have("plate", 2));

        assertTrue(resolution.complete());
        assertEquals(1, resolution.steps().size());
        assertEquals("gear", resolution.steps().get(0).recipe());
        assertEquals(Map.of("plate", 2), asMap(resolution.rawCost()));
    }

    @Test
    void aMissingIntermediateIsChainCraftedBeforeTheRootThatWantsIt() {
        Resolver.Resolution resolution = resolver().resolve("circuit", 1, have("copper", 2, "plate", 1));

        assertTrue(resolution.complete());
        assertEquals(List.of("cable", "circuit"), resolution.steps().stream().map(CraftStep::recipe).toList());
        // The leaves, and only the leaves: the cable is made by the plan, not taken from the player.
        assertEquals(Map.of("copper", 2, "plate", 1), asMap(resolution.rawCost()));
    }

    @Test
    void anIntermediateAlreadyInTheInventoryIsUsedRatherThanRemade() {
        Resolver.Resolution resolution = resolver().resolve("circuit", 1, have("cable", 3, "plate", 1));

        assertTrue(resolution.complete());
        assertEquals(List.of("circuit"), resolution.steps().stream().map(CraftStep::recipe).toList());
        assertEquals(Map.of("cable", 3, "plate", 1), asMap(resolution.rawCost()));
    }

    @Test
    void aLeafNothingMakesIsMissingAndTheWholePlanIsIncomplete() {
        Resolver.Resolution resolution = resolver().resolve("gear", 1, have());

        assertFalse(resolution.complete());
        assertEquals(Map.of("plate", 2), asMap(resolution.missing()));
        assertTrue(resolution.locked().isEmpty());
    }

    @Test
    void aLockedRecipeIsLockedAndNotMissing() {
        Resolver locked = new Resolver(recipes(), Set.of("cable")::contains);

        Resolver.Resolution resolution = locked.resolve("circuit", 1, have("copper", 9, "plate", 1));

        assertFalse(resolution.complete());
        assertEquals(Map.of("cable", 3), asMap(resolution.locked()));
        // Not folded into Missing: the player needs to know which errand they are on.
        assertTrue(resolution.missing().isEmpty());
    }

    @Test
    void craftsOfOneRecipeShareOneStepButKeepTheirOwnTime() {
        Resolver.Resolution resolution = resolver().resolve("gear", 3, have("plate", 6));

        assertEquals(1, resolution.steps().size());
        CraftStep step = resolution.steps().get(0);
        assertEquals(3, step.crafts());
        assertEquals(10, step.time(), "the time is one craft's, not the batch's");
        assertEquals(Map.of("plate", 6), asMap(step.inputs()));
        assertEquals(Map.of("gear", 3), asMap(step.outputs()));
    }

    @Test
    void aRecipeMakingTwoAtATimeRoundsUpAndKeepsTheSurplusInThePlan() {
        // One circuit wants 3 cable; cable comes 2 at a time, so 2 crafts make 4 and one is spare.
        Resolver.Resolution resolution = resolver().resolve("circuit", 1, have("copper", 2, "plate", 1));

        CraftStep cable = resolution.steps().get(0);
        assertEquals(Map.of("cable", 4), asMap(cable.outputs()));
        assertEquals(Map.of("copper", 2), asMap(cable.inputs()));
    }

    @Test
    void theSurplusOfAnOverProducingStepFeedsTheNextRequestForIt() {
        // Two circuits want 6 cable, which is exactly 3 crafts: the Resolver counts cable once across
        // the whole plan rather than per parent craft.
        Resolver.Resolution resolution = resolver().resolve("circuit", 2, have("copper", 3, "plate", 2));

        assertTrue(resolution.complete());
        assertEquals(Map.of("cable", 6), asMap(resolution.steps().get(0).outputs()));
        assertEquals(Map.of("copper", 3, "plate", 2), asMap(resolution.rawCost()));
    }

    @Test
    void theInventoryItWasHandedIsNeverSpent() {
        ItemBag inventory = have("plate", 2);

        resolver().resolve("gear", 1, inventory);

        assertEquals(2, inventory.count("plate"));
    }

    @Test
    void everyStepOutputAppearsAsSomethingToCraft() {
        Resolver.Resolution resolution = resolver().resolve("circuit", 1, have("copper", 2, "plate", 1));

        assertEquals(Map.of("cable", 4, "circuit", 1), asMap(resolution.toCraft()));
    }

    @Test
    void anUnknownRecipeResolvesToNothingRatherThanThrowing() {
        Resolver.Resolution resolution = resolver().resolve("nonesuch", 1, have("plate", 64));

        assertFalse(resolution.complete());
        assertTrue(resolution.steps().isEmpty());
    }

    @Test
    void aCycleInTheSetTerminatesAsMissingInsteadOfHanging() {
        // A recipe set is whatever a pack loaded, so the Resolver must not be the thing that hangs
        // when a pack author writes a cycle.
        AssemblingRecipeSet cyclic = AssemblingRecipeSet.builder()
                .add(recipe("a", "a", 1, 10, Ingredient.of("b", 1)))
                .add(recipe("b", "b", 1, 10, Ingredient.of("a", 1)))
                .build();

        Resolver.Resolution resolution = new Resolver(cyclic, Set.of()::contains).resolve("a", 1, have());

        assertFalse(resolution.complete());
        assertEquals(Map.of("a", 1), asMap(resolution.missing()));
    }

    @Test
    void aLockedRootIsLockedRatherThanSilentlyUnplannable() {
        Resolver locked = new Resolver(recipes(), Set.of("gear")::contains);

        Resolver.Resolution resolution = locked.resolve("gear", 4, have("plate", 64));

        assertFalse(resolution.complete());
        assertEquals(Map.of("gear", 4), asMap(resolution.locked()));
        assertTrue(resolution.missing().isEmpty());
        assertTrue(resolution.steps().isEmpty());
    }

    @Test
    void aQuantityThatWouldOverflowAnIntIsRefusedRatherThanWrappingNegative() {
        // The cap is on the craft count, not on the product of a count and an ingredient's amount. A
        // wrapped negative demand reads as already satisfied, which would report a complete plan that
        // reserves nothing and then cannot feed its own first step.
        AssemblingRecipeSet greedy = AssemblingRecipeSet.builder()
                .add(recipe("greedy", "greedy", 1, 1, Ingredient.of("leaf", 1_000_000)))
                .build();

        Resolver.Resolution resolution = new Resolver(greedy, Set.of()::contains)
                .resolve("greedy", Resolver.MAX_CRAFTS, have("leaf", Integer.MAX_VALUE));

        assertFalse(resolution.complete());
        for (ItemAmount amount : resolution.rawCost()) {
            assertTrue(amount.count() > 0, "raw cost went negative: " + amount);
        }
    }

    @Test
    void aSubCraftBeyondTheCraftLimitIsMissingRatherThanQuietlyShort() {
        // The cap has to refuse rather than clamp. A clamped sub-craft makes fewer intermediates than
        // the parent step's inputs name, and nothing downstream notices: the plan reports complete,
        // the queue takes the cost, and then throws on a step it cannot feed.
        AssemblingRecipeSet deep = AssemblingRecipeSet.builder()
                .add(recipe("sub", "sub", 1, 1, Ingredient.of("leaf", 1)))
                .add(recipe("bulk", "bulk", 1, 1, Ingredient.of("sub", 3)))
                .build();

        Resolver.Resolution resolution = new Resolver(deep, Set.of()::contains)
                .resolve("bulk", Resolver.MAX_CRAFTS / 2, have("leaf", Integer.MAX_VALUE));

        assertFalse(resolution.complete());
        assertEquals(Map.of("sub", 3 * (Resolver.MAX_CRAFTS / 2)), asMap(resolution.missing()));
    }

    @Test
    void largestAffordableIsTheBiggestCountTheInventoryCovers() {
        assertEquals(3, resolver().largestAffordable("gear", have("plate", 7)));
        assertEquals(0, resolver().largestAffordable("gear", have("plate", 1)));
    }

    @Test
    void largestAffordableCountsThroughTheChain() {
        // 5 copper makes 10 cable, enough for 3 circuits; 3 plate is the binding constraint.
        assertEquals(3, resolver().largestAffordable("circuit", have("copper", 5, "plate", 3)));
    }

    @Test
    void largestAffordableIsZeroWhenTheRecipeIsLocked() {
        Resolver locked = new Resolver(recipes(), Set.of("gear")::contains);

        assertEquals(0, locked.largestAffordable("gear", have("plate", 64)));
    }

    /** A gear that eats two of either kind of iron: a tag ingredient several items satisfy. */
    private static AssemblingRecipeSet taggedRecipes() {
        return AssemblingRecipeSet.builder()
                .add(recipe("gear", "gear", 1, 10,
                        new Ingredient(List.of("minecraft:iron_ingot", "minecraft:raw_iron"), 2)))
                .build();
    }

    @Test
    void anIngredientSeveralItemsSatisfyIsPaidWithWhicheverOneIsHeld() {
        Resolver.Resolution resolution = new Resolver(taggedRecipes(), Set.of()::contains)
                .resolve("gear", 3, have("minecraft:raw_iron", 6));

        assertTrue(resolution.complete());
        assertEquals(Map.of("minecraft:raw_iron", 6), asMap(resolution.rawCost()));
        assertEquals(Map.of("minecraft:raw_iron", 6), asMap(resolution.steps().get(0).inputs()));
    }

    @Test
    void anIngredientPaidFromTwoItemsAtOnceLandsAsOneEntryPerItem() {
        // Two entries naming the same item would each pass the queue's per-entry buffer check and
        // then together over-consume it, so the step's inputs are merged by item.
        Resolver.Resolution resolution = new Resolver(taggedRecipes(), Set.of()::contains)
                .resolve("gear", 2, have("minecraft:iron_ingot", 3, "minecraft:raw_iron", 5));

        assertTrue(resolution.complete());
        List<ItemAmount> inputs = resolution.steps().get(0).inputs();
        assertEquals(inputs.size(), asMap(inputs).size(), "an item named twice in one step: " + inputs);
        assertEquals(Map.of("minecraft:iron_ingot", 3, "minecraft:raw_iron", 1), asMap(inputs));
    }

    @Test
    void anIngredientNothingHeldSatisfiesIsMissingUnderTheNameItPrefers() {
        Resolver.Resolution resolution = new Resolver(taggedRecipes(), Set.of()::contains).resolve("gear", 1, have());

        assertFalse(resolution.complete());
        assertEquals(Map.of("minecraft:iron_ingot", 2), asMap(resolution.missing()));
    }

    /** A gear from two of either plate, and a recipe for each plate. */
    private static AssemblingRecipeSet twoPlates() {
        return AssemblingRecipeSet.builder()
                .add(recipe("gear", "gear", 1, 10, new Ingredient(List.of("fancy_plate", "plain_plate"), 2)))
                .add(recipe("fancy", "fancy_plate", 1, 10, Ingredient.of("ore", 1)))
                .add(recipe("plain", "plain_plate", 1, 10, Ingredient.of("ore", 1)))
                .build();
    }

    @Test
    void anAlternativeIsCraftedWhenTheOneAheadOfItIsLocked() {
        // Locked is the answer only when nothing acceptable can be made.
        Resolver.Resolution resolution = new Resolver(twoPlates(), Set.of("fancy")::contains)
                .resolve("gear", 1, have("ore", 8));

        assertTrue(resolution.complete());
        assertTrue(resolution.locked().isEmpty());
        assertEquals(Map.of("plain_plate", 2, "gear", 1), asMap(resolution.toCraft()));
    }

    @Test
    void anIngredientEveryAlternativeOfWhichIsLockedIsLockedAndNotMissing() {
        Resolver.Resolution resolution = new Resolver(twoPlates(), Set.of("fancy", "plain")::contains)
                .resolve("gear", 1, have("ore", 8));

        assertFalse(resolution.complete());
        assertEquals(Map.of("fancy_plate", 2), asMap(resolution.locked()));
        assertTrue(resolution.missing().isEmpty());
    }

    @Test
    void twoItemsSharingARegistryIdAreNotFoldedOntoOneAnother() {
        // ADR-0002. Potions are all minecraft:potion, told apart by a data component, so the key
        // carries the patch. A Resolver that saw only the id would answer "complete" here out of a
        // stack of the wrong potion.
        String swiftness = "minecraft:potion[minecraft:potion_contents={potion:\"minecraft:swiftness\"}]";
        String healing = "minecraft:potion[minecraft:potion_contents={potion:\"minecraft:healing\"}]";
        AssemblingRecipeSet set = AssemblingRecipeSet.builder()
                .add(recipe("swiftness", swiftness, 1, 10, Ingredient.of("sugar", 1)))
                .add(recipe("tonic", "tonic", 1, 10, Ingredient.of(swiftness, 2)))
                .build();
        Resolver resolver = new Resolver(set, Set.of()::contains);

        Resolver.Resolution fromHealing = resolver.resolve("tonic", 1, have(healing, 2));
        assertFalse(fromHealing.complete(), "a tonic is not craftable out of the other potion");
        assertEquals(Map.of("sugar", 2), asMap(fromHealing.missing()));

        Resolver.Resolution fromSwiftness = resolver.resolve("tonic", 1, have(swiftness, 2));
        assertTrue(fromSwiftness.complete());
        assertEquals(Map.of(swiftness, 2), asMap(fromSwiftness.rawCost()));
    }
}
