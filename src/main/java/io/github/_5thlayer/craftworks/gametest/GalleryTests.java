// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.Refiners;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * The Gallery pack builds a scene of each README feature in README order, through libworks'
 * {@link GalleryCheck} (libworks ADR 0010): the Personal Assembler's six features as a sign at a lectern,
 * and the placed machines working.
 */
final class GalleryTests {

    private static final BlockPos STAND = new BlockPos(13, 0, -2);
    private static final BlockPos MACHINE = new BlockPos(8, 0, -6);

    private GalleryTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("gallery_builds_a_scene_of_each_feature", GalleryCheck.TICKS,
                helper -> GalleryCheck.builds(helper, Craftworks.MOD_ID, new BlockPos(0, 1, 8), List.of(
                        new Scene("ask_for_the_item", STAND, Blocks.LECTERN),
                        new Scene("paid_up_front", STAND, Blocks.LECTERN),
                        new Scene("the_inventory", STAND, Blocks.LECTERN),
                        new Scene("crafting_plan", STAND, Blocks.LECTERN),
                        new Scene("cancel", STAND, Blocks.LECTERN),
                        new Scene("nothing_is_lost", STAND, Blocks.LECTERN),
                        new Scene("assembler_1", MACHINE, Assemblers.block(AssemblerTier.ONE).get()),
                        new Scene("assembler_2", MACHINE, Assemblers.block(AssemblerTier.TWO).get()),
                        new Scene("assembler_3", MACHINE, Assemblers.block(AssemblerTier.THREE).get()),
                        new Scene("refiner", MACHINE, Refiners.BLOCK.get()))));
    }
}
