// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;
import java.util.Set;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.api.LockHooks;
import io.github._5thlayer.craftworks.assembler.ReadyRecipes;
import io.github._5thlayer.craftworks.assembler.ReadyWatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Ready on a real player (#17, ADR-0012): the Resolver run over the whole Assembling recipe set with the
 * player's own inventory and Lock source, and the watch that runs it only while the inventory screen is
 * open.
 *
 * <p>Vanilla's own recipes are the fixture: logs make planks, planks and coal make a torch through sticks,
 * so a player holding logs and coal has the stick and torch recipes Ready with neither held.
 */
final class ReadyTests {

    /** Only players carrying this tag are locked out of the torch by the test's hook. */
    private static final String TORCH_LOCKED = "craftworks.gametest.torch_locked";

    private ReadyTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        LockHooks.register((player, recipe) -> player.entityTags().contains(TORCH_LOCKED) && recipe.toString().equals("minecraft:torch"));
        tests.test("logs_and_coal_make_the_stick_and_torch_recipes_ready", 20, ReadyTests::throughIntermediates);
        tests.test("a_locked_recipe_is_not_ready", 20, ReadyTests::lockedIsNotReady);
        tests.test("ready_is_worked_out_only_while_the_inventory_screen_is_open", 20, ReadyTests::onlyWhileWatched);
    }

    private static ServerPlayer holdingLogsAndCoal(GameTestHelper helper) {
        ServerPlayer player = AssemblerTests.playerHolding(helper, new ItemStack(Items.OAK_LOG, 4));
        player.getInventory().add(new ItemStack(Items.COAL, 1));
        return player;
    }

    private static void throughIntermediates(GameTestHelper helper) {
        List<? extends String> before = CraftworksConfig.LOCK_SOURCES.get();
        CraftworksConfig.LOCK_SOURCES.set(List.of());
        try {
            Set<String> ready = ReadyRecipes.of(holdingLogsAndCoal(helper));
            helper.assertTrue(ready.contains("minecraft:stick"), "the stick recipe is not Ready with logs held: " + ready.size() + " Ready");
            helper.assertTrue(ready.contains("minecraft:torch"), "the torch recipe is not Ready with logs and coal held");
            helper.assertFalse(ready.contains("minecraft:diamond_pickaxe"), "a pickaxe is Ready from logs and coal");
            helper.assertTrue(ReadyRecipes.of(AssemblerTests.playerHolding(helper, ItemStack.EMPTY)).isEmpty(),
                    "an empty inventory has recipes Ready");
        } finally {
            CraftworksConfig.LOCK_SOURCES.set(before);
        }
        helper.succeed();
    }

    private static void lockedIsNotReady(GameTestHelper helper) {
        List<? extends String> before = CraftworksConfig.LOCK_SOURCES.get();
        CraftworksConfig.LOCK_SOURCES.set(List.of());
        try {
            ServerPlayer player = holdingLogsAndCoal(helper);
            player.addTag(TORCH_LOCKED);
            Set<String> ready = ReadyRecipes.of(player);
            helper.assertFalse(ready.contains("minecraft:torch"), "the Locked torch recipe is Ready");
            helper.assertTrue(ready.contains("minecraft:stick"), "locking the torch took the stick recipe with it");
        } finally {
            CraftworksConfig.LOCK_SOURCES.set(before);
        }
        helper.succeed();
    }

    /**
     * Nothing is published to a player whose screen is closed, opening publishes, and closing stops the
     * ticks doing anything. The ticks are posted here, in the body, because a mock player has none of its own.
     */
    private static void onlyWhileWatched(GameTestHelper helper) {
        List<? extends String> before = CraftworksConfig.LOCK_SOURCES.get();
        CraftworksConfig.LOCK_SOURCES.set(List.of());
        try {
            ServerPlayer player = holdingLogsAndCoal(helper);
            helper.assertFalse(ReadyWatch.watching(player), "a player is watched before the client said so");
            tick(player, 100);
            helper.assertTrue(ReadyWatch.published(player).isEmpty(), "Ready was worked out for a closed inventory screen");

            ReadyWatch.watch(player, true);
            tickUntilPublished(player);
            helper.assertTrue(ReadyWatch.published(player).contains("minecraft:torch"),
                    "opening the screen did not publish a Ready set with the torch in it");

            // Closed: an inventory change and a long wait publish nothing new.
            ReadyWatch.watch(player, false);
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 9));
            tick(player, 100);
            helper.assertFalse(ReadyWatch.published(player).contains("minecraft:iron_block"),
                    "Ready was worked out again after the screen closed");

            // The same inventory, watched: the iron block is Ready, so it was only the closed screen.
            ReadyWatch.watch(player, true);
            tickUntilPublished(player);
            helper.assertTrue(ReadyWatch.published(player).contains("minecraft:iron_block"),
                    "the iron block is not Ready with nine ingots held and the screen open");
        } finally {
            CraftworksConfig.LOCK_SOURCES.set(before);
        }
        helper.succeed();
    }

    /** Ticks until a refresh has published, which takes as many ticks as the budget needs for the whole vanilla pack. */
    private static void tickUntilPublished(ServerPlayer player) {
        Set<String> before = ReadyWatch.published(player);
        for (int i = 0; i < 2_000 && ReadyWatch.published(player) == before; i++) EventHooks.firePlayerTickPost(player);
    }

    private static void tick(ServerPlayer player, int times) {
        for (int i = 0; i < times; i++) EventHooks.firePlayerTickPost(player);
    }
}
