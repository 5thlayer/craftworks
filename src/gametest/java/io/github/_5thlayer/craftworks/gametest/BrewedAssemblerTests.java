// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.gametest.AssemblerMachineTests.Placed;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.CraftRates;
import io.github._5thlayer.craftworks.machine.HoldVerdict;
import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * An Assembler brewing (#76): a tier 1 holding night vision's Brewed recipe, fed water through a Fluid Connection
 * and its items through the item capability, makes three potions in the recipe's time at its speed; a tier whose
 * config leaves out {@code brewing} refuses the recipe at Fill Recipe.
 */
final class BrewedAssemblerTests {

    private static final Identifier NIGHT_VISION = Identifier.parse(BrewedRecipesTests.NIGHT_VISION);
    private static final List<AssemblingCategory> WITHOUT_BREWING =
            List.of(AssemblingCategory.CRAFTING, AssemblingCategory.ADVANCED_CRAFTING, AssemblingCategory.CRAFTING_WITH_FLUID);

    private BrewedAssemblerTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("brewed_a_tier_1_assembler_brews_three_night_vision_from_piped_water_in_the_recipes_time", 20, BrewedAssemblerTests::brews);
        tests.test("brewed_a_tier_whose_config_leaves_out_brewing_refuses_the_recipe_at_fill_recipe", 20, BrewedAssemblerTests::refusedWithoutTheCategory);
    }

    private static void brews(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        helper.setBlock(AssemblerFluidTests.beyondFirstConnection(assembler.facing()), TestTank.BLOCK.get().defaultBlockState());
        TestTank.Entity water = helper.getBlockEntity(AssemblerFluidTests.beyondFirstConnection(assembler.facing()), TestTank.Entity.class);
        water.fill(Fluids.WATER, 8000);
        water.mode = TestTank.Mode.SOURCE;
        AssemblerMachineTests.hold(assembler, NIGHT_VISION);
        // The slots follow the recipe's ingredients: the bottles, then the wart and the carrot.
        AssemblerMachineTests.insert(assembler, 0, Items.GLASS_BOTTLE, 3);
        AssemblerMachineTests.insert(assembler, 1, Items.NETHER_WART, 1);
        AssemblerMachineTests.insert(assembler, 2, Items.GOLDEN_CARROT, 1);

        int expected = CraftRates.durationTicks(CraftworksConfig.speed(AssemblerTier.ONE), 800);
        helper.assertTrue(expected == 1600, "800 ticks at tier 1's speed are " + expected);
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        int ran = 0;
        while (potions(assembler) == 0 && ran < expected + 50) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
            ran++;
        }
        helper.assertTrue(ran == expected, "it finished its brew after " + ran + " ticks, not " + expected);
        int nightVision = 0;
        for (int n = 0; n < AssemblerSlots.OUTPUTS; n++) {
            int slot = AssemblerSlots.output(n);
            ItemStack stack = assembler.machine().inventory().getResource(slot).toStack(AssemblerMachineTests.count(assembler, slot));
            if (stack.isEmpty()) continue;
            var contents = stack.get(DataComponents.POTION_CONTENTS);
            helper.assertTrue(stack.is(Items.POTION) && contents != null && contents.potion().isPresent()
                    && contents.potion().get().is(Potions.NIGHT_VISION), "an output holds " + stack);
            nightVision += stack.getCount();
        }
        helper.assertTrue(nightVision == 3, "it made " + nightVision + " night vision, not 3");
        helper.assertTrue(AssemblerMachineTests.count(assembler, 0) == 0 && AssemblerMachineTests.count(assembler, 1) == 0
                && AssemblerMachineTests.count(assembler, 2) == 0, "the bottles, the wart or the carrot were left");
        helper.assertTrue(8000 - water.tank.getAmountAsInt(0) >= 750, "the tank gave " + (8000 - water.tank.getAmountAsInt(0)) + " mB, not 750 or more");
        helper.succeed();
    }

    private static int potions(Placed assembler) {
        int total = 0;
        for (int n = 0; n < AssemblerSlots.OUTPUTS; n++) total += AssemblerMachineTests.count(assembler, AssemblerSlots.output(n));
        return total;
    }

    private static void refusedWithoutTheCategory(GameTestHelper helper) {
        Placed held = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        helper.assertTrue(AssemblerMachineTests.request(held, NIGHT_VISION) == HoldVerdict.HELD, "tier 1 refused a Brewed recipe by default");
        helper.destroyBlock(AssemblerMachineTests.ORIGIN);
        AssemblerMachineTests.withCategories(AssemblerTier.ONE, WITHOUT_BREWING, () -> {
            Placed restricted = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
            HoldVerdict verdict = AssemblerMachineTests.request(restricted, NIGHT_VISION);
            helper.assertTrue(verdict == HoldVerdict.WRONG_CATEGORY, "a tier 1 without brewing answered a Brewed recipe with " + verdict);
            helper.assertTrue(restricted.machine().heldRecipe().isEmpty(), "a refused recipe was held");
        });
        helper.succeed();
    }
}
