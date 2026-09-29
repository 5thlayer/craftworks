// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.assembler.RuntimePlanSource;
import io.github._5thlayer.craftworks.planner.AssemblingRecipe;
import io.github._5thlayer.craftworks.planner.AssemblingRecipeSet;
import io.github._5thlayer.craftworks.planner.Ingredient;
import io.github._5thlayer.craftworks.planner.ItemAmount;
import io.github._5thlayer.craftworks.planner.Locks;
import io.github._5thlayer.craftworks.recipe.RuntimeAssemblingRecipes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * KubeJS support (#11), against the sample script in {@code kubejs/server_scripts/}: a recipe it creates
 * and one it edits through the {@code craftworks:assembling} schema, its {@code CraftworksEvents.lock}
 * listener, and a crafting recipe it adds, which converts (#13). Registered only on a
 * {@code -PwithKubeJS} run, which puts KubeJS and the script on the server; the plain run, without
 * KubeJS, is the check that Craftworks loads and works without it.
 */
final class KubeJSTests {

    private static final String DIAMOND = "craftworks:kubejs_sample/diamond";
    private static final String EMERALD = "craftworks:kubejs_sample/emerald";
    private static final String STICK = "minecraft:stick";
    private static final String LOCKED_TAG = "craftworks.kubejs_locked";
    private static final String REASON = "Sample: untag yourself to craft this";

    private KubeJSTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("a_kubejs_script_creates_an_assembling_recipe", 20, KubeJSTests::created);
        tests.test("a_kubejs_script_edits_an_assembling_recipe", 20, KubeJSTests::edited);
        tests.test("a_kubejs_crafting_recipe_converts", 20, KubeJSTests::converted);
        tests.test("a_kubejs_lock_event_locks_with_its_reason", 20, KubeJSTests::locked);
    }

    private static void created(GameTestHelper helper) {
        AssemblingRecipe diamond = RuntimeAssemblingRecipes.recipes(helper.getLevel()).byId(DIAMOND);
        helper.assertTrue(diamond != null, "the sample script's " + DIAMOND + " is not an Assembling recipe");
        helper.assertTrue(diamond.result().equals(new ItemAmount("minecraft:diamond", 2)), "it makes " + diamond.result());
        helper.assertTrue(diamond.ingredients().equals(List.of(Ingredient.of("minecraft:dirt", 1),
                Ingredient.of("minecraft:cobblestone", 3))), "it takes " + diamond.ingredients());
        helper.assertTrue(diamond.time() == 40 && diamond.priority() == 5,
                "time " + diamond.time() + " and priority " + diamond.priority() + ", not 40 and 5");
        helper.succeed();
    }

    private static void converted(GameTestHelper helper) {
        AssemblingRecipe emerald = RuntimeAssemblingRecipes.recipes(helper.getLevel()).byId(EMERALD);
        helper.assertTrue(emerald != null, "the sample script's crafting " + EMERALD + " is not a Converted recipe");
        helper.assertTrue(emerald.ingredients().equals(List.of(Ingredient.of("minecraft:dirt", 2))), "it takes " + emerald.ingredients());
        helper.succeed();
    }

    private static void edited(GameTestHelper helper) {
        AssemblingRecipeSet recipes = RuntimeAssemblingRecipes.recipes(helper.getLevel());
        AssemblingRecipe stick = recipes.byId(STICK);
        helper.assertTrue(stick != null, "the built-in " + STICK + " is not an Assembling recipe");
        helper.assertTrue(stick.time() == 20, "the script set " + STICK + "'s time to 20, it is " + stick.time());
        helper.assertTrue(stick.result().equals(new ItemAmount("minecraft:stick", 4)), "the edit changed the result");
        helper.succeed();
    }

    private static void locked(GameTestHelper helper) {
        List<? extends String> before = CraftworksConfig.LOCK_SOURCES.get();
        CraftworksConfig.LOCK_SOURCES.set(List.of());
        try {
            ServerPlayer player = AssemblerTests.playerHolding(helper, ItemStack.EMPTY);
            helper.assertFalse(RuntimePlanSource.lockedFor(player).test(DIAMOND), "the diamond is Locked for an untagged player");
            player.addTag(LOCKED_TAG);
            Locks.Reasoned<Component> locks = RuntimePlanSource.locksFor(player);
            helper.assertTrue(locks.test(DIAMOND), "the lock event did not lock the diamond for a tagged player");
            helper.assertFalse(locks.test(STICK), "the lock event locked a recipe it does not name");
            Component reason = locks.reasons().get(DIAMOND);
            helper.assertTrue(reason != null && reason.getString().equals(REASON), "the reason given is " + reason);
        } finally {
            CraftworksConfig.LOCK_SOURCES.set(before);
        }
        helper.succeed();
    }
}
