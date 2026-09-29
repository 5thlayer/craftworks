// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.planner.AssemblingRecipe;
import io.github._5thlayer.craftworks.planner.Ingredient;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import io.github._5thlayer.craftworks.recipe.ModRecipes;
import io.github._5thlayer.craftworks.recipe.RuntimeAssemblingRecipes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Mods' crafting recipes as Converted recipes (#13, ADR-0011). The game test pack's crafting recipes
 * under {@code craftworks:gametest/} stand in for another mod's.
 */
final class ModRecipesTests {

    private static final int DEFAULT_TIME = io.github._5thlayer.craftworks.recipe.AssemblingRecipe.DEFAULT_TIME;

    /** Shaped: two cobblestone over a dirt make two gravel. */
    private static final String SHAPED = "craftworks:gametest/converted_shaped";
    /** Shapeless: two sand and a dirt make three clay balls. */
    private static final String SHAPELESS = "craftworks:gametest/converted_shapeless";
    /** Shapeless, and what the reloading test excludes. */
    private static final String EXCLUDED = "craftworks:gametest/excluded_shapeless";

    private ModRecipesTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("mods_crafting_recipes_convert_at_their_ids", 20, ModRecipesTests::converts);
    }

    static void registerReloading(CraftworksGameTests.Registrar tests) {
        tests.test("excluded_and_switched_off_mod_recipes_stay_at_the_table", 600, ModRecipesTests::excludesAndOff);
    }

    private static void converts(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String id : List.of(SHAPED, SHAPELESS, EXCLUDED)) {
            if (type(server, id) != CraftworksRecipes.ASSEMBLING_TYPE.get()) {
                helper.fail(id + " should be a Converted recipe, and is " + type(server, id));
                return;
            }
        }
        var recipes = RuntimeAssemblingRecipes.recipes(helper.getLevel());
        AssemblingRecipe gravel = recipes.byId(SHAPED);
        if (gravel == null || gravel.result().count() != 2 || gravel.ingredients().size() != 2
                || gravel.time() != DEFAULT_TIME) {
            helper.fail(SHAPED + " should take two ingredients to make two gravel in the default time, and is " + gravel);
            return;
        }
        if (count(gravel, "minecraft:cobblestone") != 2 || count(gravel, "minecraft:dirt") != 1) {
            helper.fail(SHAPED + "'s grid should be a bag of two cobblestone and a dirt, and is " + gravel.ingredients());
            return;
        }
        AssemblingRecipe clay = recipes.byId(SHAPELESS);
        if (clay == null || clay.result().count() != 3 || count(clay, "minecraft:sand") != 2 || count(clay, "minecraft:dirt") != 1) {
            helper.fail(SHAPELESS + " should be two sand and a dirt making three clay balls, and is " + clay);
            return;
        }
        helper.succeed();
    }

    /**
     * Excludes one recipe, then switches conversion off, reading the recipes after each reload, and puts
     * the config back before passing or failing, since the rest of the run expects the defaults.
     */
    private static void excludesAndOff(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean();
        List<? extends String> excludedBefore = CraftworksConfig.MOD_RECIPES_EXCLUDED.get();
        boolean onBefore = CraftworksConfig.MOD_RECIPES.get();
        CraftworksConfig.MOD_RECIPES_EXCLUDED.set(List.of(EXCLUDED));
        ModRecipes.follow(server)
                .thenRun(() -> {
                    if (type(server, EXCLUDED) != RecipeType.CRAFTING || type(server, SHAPED) != CraftworksRecipes.ASSEMBLING_TYPE.get()) {
                        failure.compareAndSet(null, "excluding " + EXCLUDED + " should leave it alone and convert the rest; it is "
                                + type(server, EXCLUDED) + " and " + SHAPED + " is " + type(server, SHAPED));
                    }
                    CraftworksConfig.MOD_RECIPES.set(false);
                })
                .thenCompose(ignored -> ModRecipes.follow(server))
                .thenRun(() -> {
                    if (type(server, SHAPED) != RecipeType.CRAFTING) {
                        failure.compareAndSet(null, "with modRecipes off, " + SHAPED + " should stay crafting, and is " + type(server, SHAPED));
                    }
                })
                .handle((ignored, thrown) -> {
                    if (thrown != null) failure.compareAndSet(null, "the reload failed: " + thrown);
                    CraftworksConfig.MOD_RECIPES_EXCLUDED.set(excludedBefore);
                    CraftworksConfig.MOD_RECIPES.set(onBefore);
                    return null;
                })
                .thenCompose(ignored -> ModRecipes.follow(server))
                .whenComplete((ignored, thrown) -> done.set(true));
        helper.succeedWhen(() -> {
            helper.assertTrue(done.get(), "waiting for the reloads to finish");
            if (failure.get() != null) helper.fail(failure.get());
        });
    }

    private static int count(AssemblingRecipe recipe, String item) {
        return recipe.ingredients().stream().filter(ingredient -> ingredient.items().contains(item))
                .mapToInt(Ingredient::count).sum();
    }

    private static RecipeType<?> type(MinecraftServer server, String id) {
        return server.getRecipeManager().recipeMap().values().stream()
                .filter(holder -> holder.id().identifier().toString().equals(id))
                .map(holder -> (RecipeType<?>) holder.value().getType())
                .findFirst().orElse(null);
    }
}
