// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import org.junit.jupiter.api.Test;

/** The three category names a recipe and the server config may write, and the tiers' defaults (#24). */
class AssemblingCategoryTest {

    @Test
    void theThreeNamesReadAndNothingElseDoes() {
        for (String name : List.of("crafting", "advanced-crafting", "crafting-with-fluid")) {
            var read = AssemblingCategory.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(name));
            assertEquals(name, read.result().orElseThrow().id());
        }
        var typo = AssemblingCategory.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("advanced_crafting"));
        assertTrue(typo.isError());
        assertTrue(typo.error().orElseThrow().message().contains("advanced_crafting"));
        assertTrue(typo.error().orElseThrow().message().contains("crafting-with-fluid"));
        assertTrue(AssemblingCategory.byId("Crafting").isEmpty());
    }

    @Test
    void aRemovedCategoryFailsPointingAtCraftingWithFluid() {
        for (String name : List.of("chemistry", "oil-processing")) {
            var read = AssemblingCategory.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(name));
            assertTrue(read.isError());
            assertEquals("Category '" + name + "' was removed in Craftworks 0.6: use 'crafting-with-fluid'",
                    read.error().orElseThrow().message());
            assertTrue(AssemblingCategory.byId(name).isEmpty());
        }
    }

    @Test
    void aCategoryNeverHeldIsStillUnknownRatherThanRemoved() {
        var read = AssemblingCategory.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("refining"));
        assertTrue(read.isError());
        assertEquals("Unknown category 'refining', expected one of crafting, advanced-crafting, crafting-with-fluid",
                read.error().orElseThrow().message());
    }

    @Test
    void theConfigTakesOnlyTheThreeNames() {
        assertTrue(AssemblingCategory.isId("crafting-with-fluid"));
        assertFalse(AssemblingCategory.isId("chemistry"));
        assertFalse(AssemblingCategory.isId("oil-processing"));
        assertFalse(AssemblingCategory.isId("advanced_crafting"));
        assertFalse(AssemblingCategory.isId(""));
        assertFalse(AssemblingCategory.isId(3));
    }

    @Test
    void everyTierHoldsAllThreeCategoriesByDefault() {
        List<AssemblingCategory> all = List.of(AssemblingCategory.CRAFTING, AssemblingCategory.ADVANCED_CRAFTING,
                AssemblingCategory.CRAFTING_WITH_FLUID);
        assertEquals(all, AssemblerTier.ONE.defaultCategories());
        assertEquals(all, AssemblerTier.TWO.defaultCategories());
        assertEquals(all, AssemblerTier.THREE.defaultCategories());
    }
}
