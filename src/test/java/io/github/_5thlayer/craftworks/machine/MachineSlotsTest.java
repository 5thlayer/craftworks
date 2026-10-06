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
class MachineSlotsTest {

    private static final List<String> RECIPE = List.of("log", "sugar");

    private static final MachineSlots ASSEMBLER = AssemblerSlots.LAYOUT;

    @Test
    void theNthIngredientGoesInTheNthSlot() {
        assertEquals(Optional.of("log"), ASSEMBLER.ingredientFor(0, RECIPE));
        assertEquals(Optional.of("sugar"), ASSEMBLER.ingredientFor(1, RECIPE));
    }

    @Test
    void aSlotTheDoesNotUseTakesNothing() {
        assertEquals(Optional.empty(), ASSEMBLER.ingredientFor(2, RECIPE));
        assertFalse(ASSEMBLER.accepts(4, RECIPE, "log", String::equals));
    }

    @Test
    void anOutputSlotIsNeverAnInput() {
        assertEquals(Optional.empty(), ASSEMBLER.ingredientFor(AssemblerSlots.PRODUCT, List.of("a", "b", "c", "d", "e", "f", "g")));
        assertFalse(ASSEMBLER.isInput(AssemblerSlots.REMAINDERS));
        assertTrue(ASSEMBLER.isInput(AssemblerSlots.INPUTS - 1));
    }

    @Test
    void aSlotHoldingLessThanACraftIsShort() {
        assertTrue(ASSEMBLER.isShort(0, List.of(3), 2, count -> count));
        assertFalse(ASSEMBLER.isShort(0, List.of(3), 3, count -> count));
        assertFalse(ASSEMBLER.isShort(1, List.of(3), 0, count -> count));
    }

    @Test
    void aChemicalPlantsThirdSlotIsItsProductNotAnInput() {
        MachineSlots plant = FluidMachine.CHEMICAL_PLANT.slots();
        assertEquals(Optional.of("sugar"), plant.ingredientFor(1, RECIPE));
        assertFalse(plant.isInput(FluidMachine.CHEMICAL_PLANT.productSlot()));
        assertEquals(Optional.empty(), plant.ingredientFor(2, List.of("a", "b", "c")));
    }

    @Test
    void thereAreFiveInputsAndTwoOutputs() {
        assertEquals(5, AssemblerSlots.INPUTS);
        assertEquals(7, AssemblerSlots.SIZE);
    }
}
