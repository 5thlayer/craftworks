// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import org.junit.jupiter.api.Test;

/** The Oil Refinery's figures before a config says otherwise (#27): Factorio's oil refinery, at 1 FE = 100 J. */
class OilRefineryDefaultsTest {

    private static final MachineDefaults REFINERY = OilRefineryDefaults.INSTANCE;

    @Test
    void itRunsAtSpeedOneDrawing210FeATickWithAFiftyThousandFeBuffer() {
        assertEquals(1.0, REFINERY.defaultSpeed());
        assertEquals(210.0, REFINERY.defaultPower());
        assertEquals(50_000, REFINERY.defaultBuffer());
    }

    @Test
    void itHoldsTheOilProcessingCategoryOnly() {
        assertEquals(List.of(AssemblingCategory.OIL_PROCESSING), REFINERY.defaultCategories());
    }

    @Test
    void itsConfigSectionIsNamedForItsBlock() {
        assertEquals("oil_refinery", REFINERY.blockName());
    }

    @Test
    void itHasNoItemSlotsTwoInputBoxesAndThreeOutputBoxes() {
        FluidMachine refinery = FluidMachine.OIL_REFINERY;
        assertEquals(false, refinery.hasItemSlots());
        assertEquals(2, refinery.fluidInputs());
        assertEquals(3, refinery.fluidOutputs());
        assertEquals(5, refinery.connections().size());
    }
}
