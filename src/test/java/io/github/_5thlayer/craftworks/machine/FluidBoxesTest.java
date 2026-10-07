// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The fluid box rules (#39, ADR-0017), against the cases the FactoryWorks Pack measured from Factorio in its
 * {@code overload.json}: {@code fluid_input} (a box holds 4 crafts' worth) and {@code fluid_output_volume}
 * (3 crafts' worth, and the first result takes the unused boxes unless the recipe is pinned).
 */
class FluidBoxesTest {

    @Test
    void anInputBoxHoldsFourCraftsOfItsIngredient() {
        // Factorio's concrete, plastic bar, lubricant, heavy oil cracking's water and heavy oil, basic oil processing's crude oil.
        assertEquals(400, FluidBoxes.inputLimit(100));
        assertEquals(80, FluidBoxes.inputLimit(20));
        assertEquals(40, FluidBoxes.inputLimit(10));
        assertEquals(120, FluidBoxes.inputLimit(30));
        assertEquals(160, FluidBoxes.inputLimit(40));
        assertEquals(200, FluidBoxes.inputLimit(50));
    }

    @Test
    void anInputBoxNeverHoldsMoreThanABucket() {
        assertEquals(1000, FluidBoxes.inputLimit(250));
        assertEquals(1000, FluidBoxes.inputLimit(300));
        assertEquals(1000, FluidBoxes.inputLimit(Integer.MAX_VALUE));
    }

    private static void outputs(int boxes, boolean pinned, List<Integer> amounts, List<Integer> volumes) {
        assertEquals(volumes, FluidBoxes.outputVolumes(boxes, amounts, pinned), "amounts " + amounts + " on " + boxes + " boxes");
    }

    @Test
    void aSingleResultTakesTheBoxItsMachineLeavesUnusedAndGrowsToThreeCrafts() {
        // On two output boxes, Factorio's chemical plant: lubricant, sulfuric acid, heavy oil cracking, light oil cracking and the probes.
        outputs(2, false, List.of(10), List.of(200));
        outputs(2, false, List.of(50), List.of(200));
        outputs(2, false, List.of(30), List.of(200));
        outputs(2, false, List.of(20), List.of(200));
        outputs(2, false, List.of(60), List.of(200));
        outputs(2, false, List.of(100), List.of(300));
    }

    @Test
    void twoResultsOnTwoBoxesKeepToTheirOwn() {
        outputs(2, false, List.of(10, 10), List.of(100, 100));
    }

    @Test
    void aPinnedRecipeKeepsItsFirstResultToItsOwnBox() {
        // On three output boxes, Factorio's basic oil processing.
        outputs(3, true, List.of(45), List.of(135));
        outputs(3, true, List.of(20), List.of(100));
        outputs(3, false, List.of(45), List.of(300));
    }

    @Test
    void recipesOnThreeOutputBoxesAreSizedAsThePackMeasuredThem() {
        outputs(3, false, List.of(25, 45, 55), List.of(100, 135, 165));
        outputs(3, false, List.of(90, 20, 10), List.of(270, 100, 100));
        outputs(3, false, List.of(20), List.of(300));
        outputs(3, false, List.of(20, 20), List.of(200, 100));
    }

    @Test
    void aRecipeWithNoFluidResultSizesNothing() {
        outputs(2, false, List.of(), List.of());
    }

    @Test
    void aVolumeNeverOverflowsAnInt() {
        outputs(2, false, List.of(Integer.MAX_VALUE), List.of(Integer.MAX_VALUE));
    }
}
