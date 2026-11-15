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
        assertEquals(Optional.empty(), ASSEMBLER.ingredientFor(AssemblerSlots.OUTPUT, List.of("a", "b", "c", "d", "e", "f", "g")));
        assertFalse(ASSEMBLER.isInput(AssemblerSlots.output(1)));
        assertFalse(ASSEMBLER.isInput(AssemblerSlots.DRAIN));
        assertTrue(ASSEMBLER.isInput(AssemblerSlots.INPUTS - 1));
    }

    @Test
    void aSlotHoldingLessThanACraftIsShort() {
        assertTrue(ASSEMBLER.isShort(0, List.of(3), 2, count -> count));
        assertFalse(ASSEMBLER.isShort(0, List.of(3), 3, count -> count));
        assertFalse(ASSEMBLER.isShort(1, List.of(3), 0, count -> count));
    }

    @Test
    void thereAreFiveInputsFourSharedOutputsAndADrainAndAFillSlot() {
        assertEquals(5, AssemblerSlots.INPUTS);
        assertEquals(4, AssemblerSlots.OUTPUTS);
        assertEquals(11, AssemblerSlots.SIZE);
        assertEquals(AssemblerSlots.SIZE - 2, AssemblerSlots.DRAIN);
        assertEquals(AssemblerSlots.SIZE - 1, AssemblerSlots.FILL);
    }

    /** A save from before the shared outputs has the product in slot 5 and the remainders in 6: they are the first two outputs. */
    @Test
    void theOutputsStandWhereTheProductAndRemaindersDid() {
        assertEquals(5, AssemblerSlots.OUTPUT);
        assertEquals(AssemblerSlots.OUTPUT, ASSEMBLER.firstOutput());
        assertTrue(ASSEMBLER.isOutput(5));
        assertTrue(ASSEMBLER.isOutput(6));
        assertTrue(ASSEMBLER.isOutput(8));
        assertFalse(ASSEMBLER.isOutput(4));
        assertFalse(ASSEMBLER.isOutput(AssemblerSlots.DRAIN));
        assertFalse(ASSEMBLER.isOutput(AssemblerSlots.FILL));
    }

    @Test
    void theContainerSlotsAreTheDrainAndTheFillSlotAlone() {
        assertTrue(AssemblerSlots.isContainerSlot(AssemblerSlots.DRAIN));
        assertTrue(AssemblerSlots.isContainerSlot(AssemblerSlots.FILL));
        assertFalse(AssemblerSlots.isContainerSlot(AssemblerSlots.OUTPUT));
        assertFalse(AssemblerSlots.isContainerSlot(0));
    }

    @Test
    void aResultIsOfferedTheSlotsHoldingSomethingBeforeTheEmptyOnes() {
        assertEquals(List.of(5, 6, 7, 8), ASSEMBLER.outputOrder(slot -> false));
        assertEquals(List.of(6, 8, 5, 7), ASSEMBLER.outputOrder(slot -> slot == 6 || slot == 8));
        assertEquals(List.of(5, 6, 7, 8), ASSEMBLER.outputOrder(slot -> true));
    }

    @Test
    void theRefinerHasOneOutput() {
        assertTrue(RefinerSlots.LAYOUT.isOutput(RefinerSlots.OUTPUT));
        assertFalse(RefinerSlots.LAYOUT.isOutput(RefinerSlots.INPUT));
        assertEquals(List.of(RefinerSlots.OUTPUT), RefinerSlots.LAYOUT.outputOrder(slot -> false));
    }

    // -- spare inputs (#87) ---------------------------------------------------------------------

    /** Items stand for themselves; a variant is the item and its components, as "cheese+gold". */
    private static final List<String> CHEESE_PIZZA = List.of("cheese", "dough");

    private static boolean sameItem(String ingredient, String variant) {
        return variant.split("\\+")[0].equals(ingredient);
    }

    private static java.util.function.IntFunction<Optional<String>> holding(String... slots) {
        return slot -> slot < slots.length ? Optional.ofNullable(slots[slot]) : Optional.empty();
    }

    @Test
    void aSpareInputTakesAVariantItsOwnSlotHasNoRoomFor() {
        assertEquals(Optional.of("cheese"),
                ASSEMBLER.spareFor(2, CHEESE_PIZZA, "cheese+gold", MachineSlotsTest::sameItem, holding("cheese", "dough")));
    }

    @Test
    void aSpareInputTakesNothingTheOwnSlotCanStillTake() {
        assertEquals(Optional.empty(),
                ASSEMBLER.spareFor(2, CHEESE_PIZZA, "cheese", MachineSlotsTest::sameItem, holding("cheese", "dough")));
        assertEquals(Optional.empty(),
                ASSEMBLER.spareFor(2, CHEESE_PIZZA, "cheese+gold", MachineSlotsTest::sameItem, holding(null, "dough")));
    }

    @Test
    void aSpareInputHoldingOneVariantTakesNoOther() {
        assertEquals(Optional.empty(), ASSEMBLER.spareFor(2, CHEESE_PIZZA, "cheese+iron", MachineSlotsTest::sameItem,
                holding("cheese", "dough", "cheese+gold")));
        assertEquals(Optional.of("cheese"), ASSEMBLER.spareFor(2, CHEESE_PIZZA, "cheese+gold", MachineSlotsTest::sameItem,
                holding("cheese", "dough", "cheese+gold")));
    }

    @Test
    void anInputTheRecipeUsesIsNeverSpare() {
        assertEquals(Optional.empty(),
                ASSEMBLER.spareFor(1, CHEESE_PIZZA, "cheese+gold", MachineSlotsTest::sameItem, holding("cheese", null)));
        assertFalse(ASSEMBLER.isSpare(1, CHEESE_PIZZA.size()));
        assertTrue(ASSEMBLER.isSpare(2, CHEESE_PIZZA.size()));
        assertFalse(ASSEMBLER.isSpare(AssemblerSlots.OUTPUT, CHEESE_PIZZA.size()));
    }

    @Test
    void anIngredientIsTakenFromItsOwnSlotThenTheSpares() {
        assertEquals(List.of(1, 2, 3, 4), ASSEMBLER.sources(1, CHEESE_PIZZA.size()));
    }

    @Test
    void aResultOverflowsIntoTheSparesAfterTheOutputs() {
        assertEquals(List.of(5, 6, 7, 8, 3, 2, 4),
                ASSEMBLER.overflowOrder(CHEESE_PIZZA.size(), slot -> slot == 3));
    }
}
