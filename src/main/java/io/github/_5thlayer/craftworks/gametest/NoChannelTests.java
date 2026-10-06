// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.assembler.CraftingPlanMenu;
import io.github._5thlayer.craftworks.assembler.FillRequest;
import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.github._5thlayer.craftworks.assembler.PlanSource;
import io.github._5thlayer.craftworks.assembler.ReadyWatch;
import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.EventHooks;

/**
 * A player whose connection opened none of the Mod's channels is sent none of its payloads, and plays on.
 * NeoForge throws on a payload sent down a channel the client never opened, and a throw in a login
 * listener takes down whatever placed the player: another mod's game tests, whose mock players
 * negotiate nothing, found it.
 *
 * <p>Vanilla's mock player is the fixture, unlike the other tests' players, since it is the one that
 * logs in: datapack sync and the login event fire for it as for a real client. Nothing is checked of
 * what was sent, since any of the Mod's payloads down this connection would throw.
 */
final class NoChannelTests {

    private NoChannelTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("a_player_without_the_mods_channels_is_sent_none_of_its_payloads", 20, NoChannelTests::sentNothing);
    }

    private static void sentNothing(GameTestHelper helper) {
        // Logs in: the Assembling recipe set on datapack sync, and the queue on the login event.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<? extends String> before = CraftworksConfig.LOCK_SOURCES.get();
        CraftworksConfig.LOCK_SOURCES.set(List.of());
        try {
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 3));

            // The queue on Fill Recipe, and an open Crafting Plan's update. The logs cover the craft: one
            // they didn't would open the Crafting Plan, and opening is the answer to a packet only a client
            // with the channels sends, so the plan is put in place instead.
            PersonalAssembler.fill(player, AssemblerTests.OAK_SAPLING, FillRequest.ONE);
            helper.assertTrue(PersonalAssembler.queueOf(player).entries().size() == 1, "Fill Recipe queued nothing");
            var plan = PlanSource.ACTIVE.resolve(player, AssemblerTests.OAK_SAPLING, 1);
            player.containerMenu = new CraftingPlanMenu(1, plan.display(), 1, 0);
            PersonalAssembler.refreshPlan(player);

            // The Ready set, once the client says the inventory screen is open.
            ReadyWatch.watch(player, true);
            for (int i = 0; i < 2_000 && ReadyWatch.published(player).isEmpty(); i++) EventHooks.firePlayerTickPost(player);
            helper.assertFalse(ReadyWatch.published(player).isEmpty(), "Ready was never worked out");
            ReadyWatch.watch(player, false);

            // An Assembler's Held recipe, from its menu's tick.
            var assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
            AssemblerMachineTests.hold(assembler, AssemblerTests.OAK_SAPLING);
            AssemblerMenu menu = (AssemblerMenu) assembler.machine().createMenu(1, player.getInventory(), player);
            player.containerMenu = menu;
            menu.broadcastChanges();
        } finally {
            CraftworksConfig.LOCK_SOURCES.set(before);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
