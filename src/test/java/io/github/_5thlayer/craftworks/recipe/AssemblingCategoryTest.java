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

/** The five category names a recipe and the server config may write, and the tiers' defaults (#24). */
class AssemblingCategoryTest {

    @Test
    void theFiveFactorioNamesReadAndNothingElseDoes() {
        for (String name : List.of("crafting", "advanced-crafting", "crafting-with-fluid", "chemistry", "oil-processing")) {
            var read = AssemblingCategory.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(name));
            assertEquals(name, read.result().orElseThrow().id());
        }
        var typo = AssemblingCategory.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("advanced_crafting"));
        assertTrue(typo.isError());
        assertTrue(typo.error().orElseThrow().message().contains("advanced_crafting"));
        assertTrue(AssemblingCategory.byId("Crafting").isEmpty());
    }

    @Test
    void theConfigTakesOnlyTheFiveNames() {
        assertTrue(AssemblingCategory.isId("chemistry"));
        assertFalse(AssemblingCategory.isId("advanced_crafting"));
        assertFalse(AssemblingCategory.isId(""));
        assertFalse(AssemblingCategory.isId(3));
    }

    @Test
    void tier1TakesCraftingAndTiersTwoAndThreeAddFluid() {
        assertEquals(List.of(AssemblingCategory.CRAFTING, AssemblingCategory.ADVANCED_CRAFTING),
                AssemblerTier.ONE.defaultCategories());
        List<AssemblingCategory> more = List.of(AssemblingCategory.CRAFTING, AssemblingCategory.ADVANCED_CRAFTING,
                AssemblingCategory.CRAFTING_WITH_FLUID);
        assertEquals(more, AssemblerTier.TWO.defaultCategories());
        assertEquals(more, AssemblerTier.THREE.defaultCategories());
    }
}
