// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.assembler.CraftingPlanMenu;
import io.github._5thlayer.craftworks.assembler.FillRequest;
import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.github._5thlayer.craftworks.planner.ItemAmount;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Crafting Plan on a real player (#7): what opens it, that opening queues nothing, and that it is
 * the server's resolution the menu carries.
 */
final class CraftingPlanTests {

    private CraftingPlanTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("a_middle_click_opens_the_plan_and_queues_nothing", 20, CraftingPlanTests::middleClick);
        tests.test("a_request_that_cannot_start_opens_the_plan", 20, CraftingPlanTests::cannotStart);
        tests.test("a_plus_one_queues_and_the_plan_stays_open", 20, CraftingPlanTests::plusOne);
    }

    private static void middleClick(GameTestHelper helper) {
        ServerPlayer player = AssemblerTests.playerHolding(helper, new ItemStack(Items.OAK_LOG, 4));
        PersonalAssembler.fill(player, AssemblerTests.OAK_SAPLING, FillRequest.PLAN);
        CraftingPlanMenu menu = openPlan(helper, player);
        helper.assertTrue(PersonalAssembler.queueOf(player).isEmpty(), "opening the plan queued something");
        helper.assertTrue(AssemblerTests.count(player, Items.OAK_LOG) == 4, "opening the plan took items");
        helper.assertTrue(menu.display().complete(), "two logs of four should plan complete");
        helper.assertTrue(menu.buttons().allCount() == 2, "all should be 2 crafts, and is " + menu.buttons().allCount());
        helper.succeed();
    }

    /** Five saplings want ten logs; one log affords none, so the plan opens naming the logs Missing. */
    private static void cannotStart(GameTestHelper helper) {
        ServerPlayer player = AssemblerTests.playerHolding(helper, new ItemStack(Items.OAK_LOG, 1));
        PersonalAssembler.fill(player, AssemblerTests.OAK_SAPLING, FillRequest.FIVE);
        CraftingPlanMenu menu = openPlan(helper, player);
        helper.assertTrue(PersonalAssembler.queueOf(player).isEmpty(), "a request the inventory cannot cover queued");
        helper.assertFalse(menu.display().complete(), "one log should not plan a sapling complete");
        helper.assertTrue(menu.display().missing().stream().map(ItemAmount::item).anyMatch(item -> item.contains("oak_log")),
                "the plan should name the logs Missing, and lists " + menu.display().missing());
        helper.assertFalse(menu.buttons().one(), "+1 is lit with nothing affordable");
        helper.succeed();
    }

    private static void plusOne(GameTestHelper helper) {
        ServerPlayer player = AssemblerTests.playerHolding(helper, new ItemStack(Items.OAK_LOG, 4));
        PersonalAssembler.openPlan(player, AssemblerTests.OAK_SAPLING);
        CraftingPlanMenu menu = openPlan(helper, player);
        PersonalAssembler.craft(player, menu.display().recipe(), 1);
        helper.assertTrue(PersonalAssembler.queueOf(player).entries().size() == 1, "+1 did not queue");
        helper.assertTrue(player.containerMenu == menu, "the plan closed after a press");
        helper.succeed();
    }

    private static CraftingPlanMenu openPlan(GameTestHelper helper, ServerPlayer player) {
        if (player.containerMenu instanceof CraftingPlanMenu menu) return menu;
        throw helper.assertionException("the Crafting Plan is not open; the player has " + player.containerMenu);
    }
}
