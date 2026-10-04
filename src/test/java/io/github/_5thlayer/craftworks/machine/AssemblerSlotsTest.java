// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Which ingredient each input slot takes (#21): the nth in the nth, and a slot the recipe doesn't use, nothing. */
class AssemblerSlotsTest {

    private static final List<String> RECIPE = List.of("log", "sugar");

    @Test
    void theNthIngredientGoesInTheNthSlot() {
        assertEquals(Optional.of("log"), AssemblerSlots.ingredientFor(0, RECIPE));
        assertEquals(Optional.of("sugar"), AssemblerSlots.ingredientFor(1, RECIPE));
    }

    @Test
    void aSlotTheDoesNotUseTakesNothing() {
        assertEquals(Optional.empty(), AssemblerSlots.ingredientFor(2, RECIPE));
        assertFalse(AssemblerSlots.accepts(4, RECIPE, "log", String::equals));
    }

    @Test
    void anOutputSlotIsNeverAnInput() {
        assertEquals(Optional.empty(), AssemblerSlots.ingredientFor(AssemblerSlots.PRODUCT, List.of("a", "b", "c", "d", "e", "f", "g")));
        assertFalse(AssemblerSlots.isInput(AssemblerSlots.REMAINDERS));
        assertTrue(AssemblerSlots.isInput(AssemblerSlots.INPUTS - 1));
    }

    @Test
    void aSlotHoldingLessThanACraftIsShort() {
        assertTrue(AssemblerSlots.isShort(0, List.of(3), 2, count -> count));
        assertFalse(AssemblerSlots.isShort(0, List.of(3), 3, count -> count));
        assertFalse(AssemblerSlots.isShort(1, List.of(3), 0, count -> count));
    }

    @Test
    void thereAreFiveInputsAndTwoOutputs() {
        assertEquals(5, AssemblerSlots.INPUTS);
        assertEquals(7, AssemblerSlots.SIZE);
    }
}
