// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.function.Predicate;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.api.LockHooks;
import io.github._5thlayer.craftworks.assembler.FillRequest;
import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.github._5thlayer.craftworks.assembler.RuntimePlanSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Lock source on a real player (#10): the configured {@code lockSource} and a registered hook,
 * each locking what the other does not, and a Locked recipe queueing nothing.
 */
final class LockTests {

    /** Only players carrying this tag are locked by the test's hook, so no other test sees it. */
    private static final String LOCKED_BY_HOOK = "craftworks.gametest.locked_by_hook";

    private static final Identifier SLIME_BALL = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "gametest/slime_ball");

    private LockTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        LockHooks.register((player, recipe) -> player.entityTags().contains(LOCKED_BY_HOOK) && recipe.equals(SLIME_BALL));
        tests.test("the_recipe_book_and_a_hook_each_lock", 20, LockTests::bothLock);
    }

    private static void bothLock(GameTestHelper helper) {
        CraftworksConfig.LockSource before = CraftworksConfig.LOCK_SOURCE.get();
        CraftworksConfig.LOCK_SOURCE.set(CraftworksConfig.LockSource.recipeBook);
        try {
            ServerPlayer player = AssemblerTests.playerHolding(helper, new ItemStack(Items.OAK_LOG, 2));
            player.addTag(LOCKED_BY_HOOK);
            // The slime ball is in the book, so only the hook locks it; the sapling is not, so only the book does.
            player.getRecipeBook().add(ResourceKey.create(Registries.RECIPE, SLIME_BALL));
            Predicate<String> locked = RuntimePlanSource.lockedFor(player);
            helper.assertTrue(locked.test(AssemblerTests.OAK_SAPLING.toString()), "the recipe book did not lock the sapling");
            helper.assertTrue(locked.test(SLIME_BALL.toString()), "the hook did not lock the slime ball");

            // Config and hook read here and restored before returning: nothing else runs in between, since
            // a test body runs whole on the server thread.
            CraftworksConfig.LOCK_SOURCE.set(CraftworksConfig.LockSource.none);
            helper.assertTrue(RuntimePlanSource.lockedFor(player).test(SLIME_BALL.toString()),
                    "the hook alone did not lock the slime ball");
            helper.assertFalse(RuntimePlanSource.lockedFor(player).test(AssemblerTests.OAK_SAPLING.toString()),
                    "with lockSource none the sapling is still Locked");
            CraftworksConfig.LOCK_SOURCE.set(CraftworksConfig.LockSource.recipeBook);

            player.removeTag(LOCKED_BY_HOOK);
            helper.assertFalse(RuntimePlanSource.lockedFor(player).test(SLIME_BALL.toString()),
                    "the slime ball is still Locked with the hook saying no and the book holding it");

            PersonalAssembler.fill(player, AssemblerTests.OAK_SAPLING, FillRequest.ONE);
            helper.assertTrue(PersonalAssembler.queueOf(player).isEmpty(), "a Locked recipe was queued");
            helper.assertTrue(AssemblerTests.count(player, Items.OAK_LOG) == 2, "a Locked recipe took its cost");
        } finally {
            CraftworksConfig.LOCK_SOURCE.set(before);
        }
        helper.succeed();
    }
}
