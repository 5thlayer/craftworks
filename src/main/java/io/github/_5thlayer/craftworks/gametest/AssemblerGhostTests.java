// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.Optional;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.MachineGhosts;
import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.machine.HeldRecipeView;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.HeldRecipes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The Assembler screen's ghost lookup (#30), which EMI's and JEI's shortcuts ask: which ghost, if any, is at
 * a point, read from a real menu on a real server, since the lookup names no client type. The screen only adds
 * its corner and the clock.
 */
final class AssemblerGhostTests {

    // Milk and lime dye for four slime balls, in the game tests' pack.
    private static final Identifier SLIME = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "gametest/slime_ball");

    // The panel's corner is taken as (0, 0), so a slot's own x and y are the point over it.
    private static final int INPUT_Y = AssemblerMenu.INPUT_Y;

    private AssemblerGhostTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("an_empty_input_slot_has_the_ingredient_it_takes_as_its_ghost", 20, AssemblerGhostTests::inputs);
        tests.test("a_tag_ingredients_ghost_is_the_member_shown_at_that_moment", 20, AssemblerGhostTests::tagMember);
        tests.test("the_product_slot_and_the_head_have_the_product_as_their_ghost", 20, AssemblerGhostTests::product);
        tests.test("no_ghost_over_a_filled_slot_an_unused_slot_the_remainders_or_the_panel", 20, AssemblerGhostTests::nothing);
        tests.test("no_ghost_anywhere_with_no_held_recipe", 20, AssemblerGhostTests::noHeldRecipe);
    }

    /** The menu a player has open on a placed Assembler, with the Held recipe the server would have sent it. */
    private static AssemblerMenu open(AssemblerMachineTests.Placed assembler, Identifier recipe) {
        ServerPlayer player = assembler.player();
        AssemblerMenu menu = (AssemblerMenu) assembler.machine().createMenu(1, player.getInventory(), player);
        player.containerMenu = menu;
        var held = HeldRecipes.find(assembler.helper().getLevel(), recipe).orElseThrow().value();
        menu.show(Optional.of(new HeldRecipeView(recipe, held.ingredients(), held.results())));
        return menu;
    }

    private static Optional<MachineGhosts.Ghost> at(AssemblerMenu menu, int x, int y, long millis) {
        return MachineGhosts.at(menu, 0, 0, x, y, millis);
    }

    private static int inputX(int slot) {
        return AssemblerMenu.INPUT_X + slot * 18;
    }

    private static void inputs(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMachineTests.hold(assembler, SLIME);
        AssemblerMenu menu = open(assembler, SLIME);
        // Anywhere over the slot's 16 by 16, and not the pixel beside it.
        expect(helper, at(menu, inputX(0), INPUT_Y, 0), Items.MILK_BUCKET, 1, inputX(0), INPUT_Y);
        expect(helper, at(menu, inputX(0) + 15, INPUT_Y + 15, 0), Items.MILK_BUCKET, 1, inputX(0), INPUT_Y);
        expect(helper, at(menu, inputX(1) + 8, INPUT_Y + 8, 0), Items.LIME_DYE, 1, inputX(1), INPUT_Y);
        helper.assertTrue(at(menu, inputX(0) + 16, INPUT_Y, 0).isEmpty(), "the pixel right of a slot has a ghost");
        helper.assertTrue(at(menu, inputX(0), INPUT_Y - 1, 0).isEmpty(), "the pixel above a slot has a ghost");
        // The screen's corner moves the slots.
        expect(helper, MachineGhosts.at(menu, 10, 20, 10 + inputX(1), 20 + INPUT_Y, 0), Items.LIME_DYE, 1,
                10 + inputX(1), 20 + INPUT_Y);
        helper.succeed();
    }

    private static void tagMember(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMachineTests.hold(assembler, AssemblerTests.OAK_SAPLING);
        AssemblerMenu menu = open(assembler, AssemblerTests.OAK_SAPLING);
        var members = menu.held().orElseThrow().ingredients().get(0).ingredient().items().toList();
        helper.assertTrue(members.size() > 1, "the oak logs tag has " + members.size() + " members");
        for (int n = 0; n < members.size() + 1; n++) {
            Item shown = members.get(n % members.size()).value();
            // Each member for a second, then round again, and the count is the recipe's whichever shows.
            expect(helper, at(menu, inputX(0), INPUT_Y, n * MachineGhosts.CYCLE_MILLIS + 999), shown, 2, inputX(0), INPUT_Y);
        }
        helper.succeed();
    }

    private static void product(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMachineTests.hold(assembler, SLIME);
        AssemblerMenu menu = open(assembler, SLIME);
        expect(helper, at(menu, AssemblerMenu.PRODUCT_X, INPUT_Y, 0), Items.SLIME_BALL, 4, AssemblerMenu.PRODUCT_X, INPUT_Y);
        expect(helper, at(menu, MachineGhosts.HEAD_X + 3, MachineGhosts.HEAD_Y + 3, 0), Items.SLIME_BALL, 4,
                MachineGhosts.HEAD_X, MachineGhosts.HEAD_Y);
        helper.succeed();
    }

    private static void nothing(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMachineTests.hold(assembler, SLIME);
        AssemblerMenu menu = open(assembler, SLIME);
        // A real stack answers for itself: the first input holds milk, and the product slot slime balls.
        AssemblerMachineTests.insert(assembler, 0, Items.MILK_BUCKET, 1);
        assembler.machine().inventory().set(AssemblerSlots.PRODUCT, ItemResource.of(Items.SLIME_BALL), 1);
        helper.assertTrue(at(menu, inputX(0), INPUT_Y, 0).isEmpty(), "a filled input has a ghost");
        helper.assertTrue(at(menu, AssemblerMenu.PRODUCT_X, INPUT_Y, 0).isEmpty(), "a filled product slot has a ghost");
        // The slime ball recipe takes two ingredients, so the third input takes nothing, and the remainders slot is never ghosted.
        helper.assertTrue(at(menu, inputX(2), INPUT_Y, 0).isEmpty(), "an unused input has a ghost");
        helper.assertTrue(at(menu, AssemblerMenu.REMAINDERS_X, INPUT_Y, 0).isEmpty(), "the remainders slot has a ghost");
        // The panel, and the player's inventory below it.
        helper.assertTrue(at(menu, 0, 0, 0).isEmpty(), "the panel's corner has a ghost");
        helper.assertTrue(at(menu, 100, 20, 0).isEmpty(), "the head's name has a ghost");
        helper.assertTrue(at(menu, 8, AssemblerMenu.INVENTORY_Y, 0).isEmpty(), "an inventory slot has a ghost");
        helper.succeed();
    }

    private static void noHeldRecipe(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        ServerPlayer player = assembler.player();
        AssemblerMenu menu = (AssemblerMenu) assembler.machine().createMenu(1, player.getInventory(), player);
        helper.assertTrue(at(menu, inputX(0), INPUT_Y, 0).isEmpty(), "an input has a ghost with no Held recipe");
        helper.assertTrue(at(menu, AssemblerMenu.PRODUCT_X, INPUT_Y, 0).isEmpty(), "the product slot has a ghost with no Held recipe");
        helper.assertTrue(at(menu, MachineGhosts.HEAD_X, MachineGhosts.HEAD_Y, 0).isEmpty(), "the head has a ghost with no Held recipe");
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, Optional<MachineGhosts.Ghost> ghost, Item item, int count, int x, int y) {
        helper.assertTrue(ghost.isPresent(), "no ghost where " + item + " was expected");
        ItemStack stack = ghost.get().stack();
        helper.assertTrue(stack.is(item) && stack.getCount() == count, "the ghost is " + stack + ", not " + count + " " + item);
        helper.assertTrue(ghost.get().x() == x && ghost.get().y() == y, "the ghost stands at " + ghost.get().x() + ", " + ghost.get().y());
    }
}
