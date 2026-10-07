// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.AssemblerBlockEntity;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.MachineState;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.HeldRecipes;
import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The state an Assembler reports (#28), which Jade shows: the one method the plugin reads, on a real server,
 * for each state and with no Held recipe. Each is checked against what the tick then does, since the report
 * is only worth showing if it agrees with it.
 */
final class AssemblerStateTests {

    private static final Identifier CAKE = Identifier.parse("minecraft:cake");
    private static final Identifier CATEGORY_FLUID = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "gametest/category_fluid");
    private static final Identifier NOT_A_RECIPE = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "gametest/not_a_recipe");

    private AssemblerStateTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("an_assembler_with_no_held_recipe_reports_no_recipe", 20, AssemblerStateTests::noRecipe);
        tests.test("an_assembler_reports_crafting_with_its_inputs_room_and_power", 20, AssemblerStateTests::crafting);
        tests.test("an_assembler_reports_needs_power_when_the_buffer_cannot_pay_the_tick", 20, AssemblerStateTests::needsPower);
        tests.test("an_assembler_reports_missing_ingredients_when_an_input_holds_less_than_one_craft", 20, AssemblerStateTests::missingIngredients);
        tests.test("an_assembler_reports_output_full_when_the_product_slot_cannot_take_a_craft", 20, AssemblerStateTests::productFull);
        tests.test("an_assembler_reports_output_full_when_the_remainder_slot_cannot_take_a_craft", 20, AssemblerStateTests::remaindersFull);
        tests.test("an_assembler_reports_cant_run_for_a_held_recipe_it_cannot_run_or_that_is_gone", 20, AssemblerStateTests::cantRun);
    }

    private static void noRecipe(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        power(assembler);
        expect(helper, assembler, MachineState.NO_RECIPE, false);
        helper.succeed();
    }

    private static void crafting(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMachineTests.hold(assembler, AssemblerTests.OAK_SAPLING);
        AssemblerMachineTests.insert(assembler, 0, Items.OAK_LOG, 2);
        power(assembler);
        expect(helper, assembler, MachineState.CRAFTING, true);
        helper.succeed();
    }

    private static void needsPower(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMachineTests.hold(assembler, AssemblerTests.OAK_SAPLING);
        AssemblerMachineTests.insert(assembler, 0, Items.OAK_LOG, 2);
        expect(helper, assembler, MachineState.NEEDS_POWER, false);
        // Less than a tick's share is still short.
        power(assembler, 1);
        expect(helper, assembler, MachineState.NEEDS_POWER, false);
        helper.succeed();
    }

    private static void missingIngredients(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMachineTests.hold(assembler, AssemblerTests.OAK_SAPLING);
        power(assembler);
        expect(helper, assembler, MachineState.MISSING_INGREDIENTS, false);
        // One log is less than the two a craft takes.
        AssemblerMachineTests.insert(assembler, 0, Items.OAK_LOG, 1);
        expect(helper, assembler, MachineState.MISSING_INGREDIENTS, false);
        helper.succeed();
    }

    /** Full, it is the first thing the tick asks, so it reports so unpowered too. */
    private static void productFull(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, AssemblerTests.OAK_SAPLING);
        AssemblerMachineTests.insert(assembler, 0, Items.OAK_LOG, 2);
        assembler.machine().inventory().set(AssemblerSlots.PRODUCT, ItemResource.of(Items.OAK_SAPLING), 64);
        expect(helper, assembler, MachineState.OUTPUT_FULL, false);
        power(assembler);
        expect(helper, assembler, MachineState.OUTPUT_FULL, false);
        helper.succeed();
    }

    /** Cake's buckets cannot join a stick in the remainder slot. */
    private static void remaindersFull(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, CAKE);
        List<SizedIngredient> bag = HeldRecipes.find(helper.getLevel(), CAKE).orElseThrow().value().ingredients();
        for (int slot = 0; slot < bag.size(); slot++) {
            AssemblerMachineTests.insert(assembler, slot, bag.get(slot).ingredient().items().findFirst().orElseThrow().value(),
                    bag.get(slot).count());
        }
        assembler.machine().inventory().set(AssemblerSlots.REMAINDERS, ItemResource.of(Items.STICK), 1);
        power(assembler);
        expect(helper, assembler, MachineState.OUTPUT_FULL, false);
        helper.succeed();
    }

    private static void cantRun(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        power(assembler);
        // Not through Fill Recipe, which refuses both: a pack's reload or a changed recipe leaves them held. A recipe
        // in a category the tier's config no longer lists is one it can't run.
        AssemblerMachineTests.withCategories(AssemblerTier.ONE, List.of(AssemblingCategory.CRAFTING), () -> {
            assembler.machine().setHeldRecipe(CATEGORY_FLUID, assembler.player());
            expect(helper, assembler, MachineState.CANT_RUN, false);
        });
        assembler.machine().setHeldRecipe(NOT_A_RECIPE, assembler.player());
        expect(helper, assembler, MachineState.CANT_RUN, false);
        helper.succeed();
    }

    /**
     * The state is the one given, reading it changes nothing, and a tick agrees: it moves the craft on
     * exactly when the state is Crafting.
     */
    private static void expect(GameTestHelper helper, AssemblerMachineTests.Placed assembler, MachineState state, boolean advances) {
        AssemblerBlockEntity machine = assembler.machine();
        int energy = machine.energy();
        int progress = machine.craftProgress();
        MachineState reported = machine.state();
        helper.assertTrue(reported == state, "the Assembler reported " + reported + ", not " + state);
        helper.assertTrue(machine.state() == state, "asking again reported " + machine.state());
        helper.assertTrue(machine.energy() == energy && machine.craftProgress() == progress, "asking the state changed the Assembler");
        machine.serverTick(helper.getLevel());
        helper.assertTrue((machine.craftProgress() > progress) == advances,
                "a tick that was " + state + " " + (advances ? "made no progress" : "made progress"));
    }

    private static void power(AssemblerMachineTests.Placed assembler) {
        power(assembler, 10_000);
    }

    private static void power(AssemblerMachineTests.Placed assembler, int fe) {
        try (Transaction tx = Transaction.openRoot()) {
            assembler.energy(Direction.UP).insert(fe, tx);
            tx.commit();
        }
    }
}
