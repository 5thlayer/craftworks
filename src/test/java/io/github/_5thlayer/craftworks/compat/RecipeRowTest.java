// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** An Assembling recipe is as wide as its slots or its widest line of text, whichever is wider (#33). */
class RecipeRowTest {

    @Test
    void slotsSetTheWidthWhenTheTextIsNarrower() {
        // Three ingredients and two results: 3 * 18 + 30 + 2 * 18.
        assertEquals(120, RecipeRow.width(3, 2, 20, 60));
    }

    @Test
    void aLongCategoryWidensANarrowRecipe() {
        // One ingredient, one result: 66 of slots, under a 150-wide "Category: crafting-with-fluid".
        assertEquals(150, RecipeRow.width(1, 1, 20, 150));
    }

    @Test
    void theSecondsLineCountsFromWhereItStarts() {
        // It starts past the one ingredient, at 18 + 3, so it ends at 21 + 60 = 81, past the slots' 66.
        assertEquals(81, RecipeRow.width(1, 1, 60, 40));
    }

    @Test
    void aWiderTextIsWiderStillThanASecondsLineThatFits() {
        assertEquals(100, RecipeRow.width(1, 1, 20, 100));
    }

    @Test
    void aFluidIngredientsSlotIsCountedAsAnInput() {
        // Sand and water to clay balls: two input slots, one output.
        assertEquals(2 * 18 + 30 + 18, RecipeRow.width(2, 1, 10, 10));
    }
}
