// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.Refiners;
import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Gallery pack builds a scene of each README feature in README order, in rows of three, through
 * libworks' {@link GalleryCheck} pieces (libworks ADR 0010): the Personal Assembler's six features as a
 * sign at a lectern, and the placed machines working. {@link GalleryCheck#builds} lays its scenes in one
 * row, so the rows are checked here. A scene's sign, clicked, builds the scene again.
 */
final class GalleryTests {

    private static final BlockPos ORIGIN = new BlockPos(0, 1, 8);
    private static final BlockPos STAND = new BlockPos(13, 0, -2);
    private static final BlockPos MACHINE = new BlockPos(8, 0, -6);
    private static final BlockPos SIGN = new BlockPos(13, 0, 0);
    private static final int ROW = 3;
    private static final int ROW_DEPTH = 16;

    private static final List<Scene> SCENES = List.of(
            new Scene("ask_for_the_item", STAND, Blocks.LECTERN),
            new Scene("paid_up_front", STAND, Blocks.LECTERN),
            new Scene("the_inventory", STAND, Blocks.LECTERN),
            new Scene("crafting_plan", STAND, Blocks.LECTERN),
            new Scene("cancel", STAND, Blocks.LECTERN),
            new Scene("nothing_is_lost", STAND, Blocks.LECTERN),
            new Scene("assembler_1", MACHINE, Assemblers.block(AssemblerTier.ONE).get()),
            new Scene("assembler_2", MACHINE, Assemblers.block(AssemblerTier.TWO).get()),
            new Scene("assembler_3", MACHINE, Assemblers.block(AssemblerTier.THREE).get()),
            new Scene("refiner", MACHINE, Refiners.BLOCK.get()));

    private GalleryTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("gallery_builds_a_scene_of_each_feature", GalleryCheck.TICKS, GalleryTests::builds);
        tests.test("gallery_scenes_sign_clicked_builds_the_scene_again", GalleryCheck.TICKS, GalleryTests::signReplays);
    }

    private static void builds(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var pack = GalleryPack.id(Craftworks.MOD_ID);
        GalleryCheck.assertOfferedAndOff(helper, pack);
        var source = GalleryCheck.operator(helper, helper.absolutePos(ORIGIN));
        forceLoad(helper);
        var on = GalleryCheck.select(server, pack, true);
        clearAwayIfItFails(helper, pack, on);
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), pack + " is not on yet"))
                .thenWaitUntil(() -> helper.assertTrue(loaded(helper), "the gallery's chunks are not loaded yet"))
                .thenExecute(() -> {
                    GalleryCheck.assertLoads(helper, source, Craftworks.MOD_ID);
                    for (var scene : SCENES) {
                        helper.assertTrue(server.getFunctions().get(GalleryPack.scene(Craftworks.MOD_ID, scene.name())).isPresent(),
                                "there is no scene function " + GalleryPack.scene(Craftworks.MOD_ID, scene.name()));
                    }
                    GalleryCheck.assertRuns(helper, source, GalleryPack.build(Craftworks.MOD_ID));
                    for (int i = 0; i < SCENES.size(); i++) {
                        var scene = SCENES.get(i);
                        var pos = sceneOrigin(helper, i).offset(scene.key());
                        helper.assertTrue(helper.getLevel().getBlockState(pos).is(scene.block()),
                                "scene " + scene.name() + "'s " + scene.block() + " is not at " + pos.toShortString()
                                        + ", where stands " + helper.getLevel().getBlockState(pos));
                        var sign = sceneOrigin(helper, i).offset(SIGN);
                        helper.assertTrue(helper.getLevel().getBlockState(sign).is(Blocks.OAK_SIGN),
                                "scene " + scene.name() + " has no sign at " + sign.toShortString());
                    }
                    off.set(clearAway(helper, pack));
                })
                .thenWaitUntil(() -> helper.assertTrue(off.get() != null && off.get().isDone(), pack + " is not off yet"))
                .thenSucceed();
    }

    /** A scene's sign, clicked, builds the scene again where it stands: here the first Assembler's, taken away. */
    private static void signReplays(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var pack = GalleryPack.id(Craftworks.MOD_ID);
        var source = GalleryCheck.operator(helper, helper.absolutePos(ORIGIN));
        forceLoad(helper);
        var on = GalleryCheck.select(server, pack, true);
        clearAwayIfItFails(helper, pack, on);
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), pack + " is not on yet"))
                .thenWaitUntil(() -> helper.assertTrue(loaded(helper), "the gallery's chunks are not loaded yet"))
                .thenExecute(() -> {
                    GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Craftworks.MOD_ID, "assembler_1"));
                    var here = helper.absolutePos(ORIGIN);
                    var built = here.offset(MACHINE);
                    helper.getLevel().removeBlock(built, false);
                    var clicked = here.offset(SIGN);
                    helper.assertTrue(helper.getLevel().getBlockEntity(clicked) instanceof SignBlockEntity entity
                                    && entity.executeClickCommandsIfPresent(helper.getLevel(), source.getPlayer(), clicked, true),
                            "the scene's sign runs no command when clicked");
                    helper.assertTrue(helper.getLevel().getBlockState(built).is(Assemblers.block(AssemblerTier.ONE).get()),
                            "clicking the sign did not build the Assembler again where it stood");
                    off.set(clearAway(helper, pack));
                })
                .thenWaitUntil(() -> helper.assertTrue(off.get() != null && off.get().isDone(), pack + " is not off yet"))
                .thenSucceed();
    }

    /** Where scene {@code index} is run from, absolute: three to a row, each row 16 blocks north of the last. */
    private static BlockPos sceneOrigin(GameTestHelper helper, int index) {
        return helper.absolutePos(ORIGIN).east(GalleryCheck.PLOT_WIDTH * (index % ROW)).north(ROW_DEPTH * (index / ROW));
    }

    private static BoundingBox strip(GameTestHelper helper) {
        var boxes = new java.util.ArrayList<BoundingBox>();
        for (int i = 0; i < SCENES.size(); i++) boxes.add(GalleryCheck.plot(sceneOrigin(helper, i)));
        return BoundingBox.encapsulatingBoxes(boxes).orElseThrow();
    }

    /** Keeps the chunks the whole gallery stands in loaded, as a player standing where it is run would. */
    private static void forceLoad(GameTestHelper helper) {
        strip(helper).intersectingChunks().forEach(chunk -> helper.getLevel().setChunkForced(chunk.x(), chunk.z(), true));
    }

    private static boolean loaded(GameTestHelper helper) {
        return strip(helper).intersectingChunks().allMatch(chunk -> helper.getLevel().hasChunk(chunk.x(), chunk.z()));
    }

    /** Takes away what the gallery built, plot by plot, and turns the pack off. */
    private static CompletableFuture<Void> clearAway(GameTestHelper helper, String pack) {
        for (int i = 0; i < SCENES.size(); i++) {
            StructureUtils.clearSpaceForStructure(GalleryCheck.plot(sceneOrigin(helper, i)), helper.getLevel());
        }
        return GalleryCheck.select(helper.getLevel().getServer(), pack, false);
    }

    /** A failed test skips its own last steps, which would leave the pack on under the batches after it. */
    private static void clearAwayIfItFails(GameTestHelper helper, String pack, CompletableFuture<Void> on) {
        ServerLevel level = helper.getLevel();
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo testInfo) {
            }

            @Override
            public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
            }

            @Override
            public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                for (int i = 0; i < SCENES.size(); i++) {
                    StructureUtils.clearSpaceForStructure(GalleryCheck.plot(sceneOrigin(helper, i)), level);
                }
                var server = level.getServer();
                on.whenComplete((done, failed) -> server.execute(() -> GalleryCheck.select(server, pack, false)));
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
            }
        });
    }
}
