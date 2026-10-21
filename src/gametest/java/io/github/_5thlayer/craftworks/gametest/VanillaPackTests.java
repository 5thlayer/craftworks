// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.planner.AssemblingRecipe;
import io.github._5thlayer.craftworks.planner.Ingredient;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import io.github._5thlayer.craftworks.recipe.RuntimeAssemblingRecipes;
import io.github._5thlayer.craftworks.recipe.VanillaPack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The built-in vanilla pack (#9): with the {@code vanillaRecipes} flag on, vanilla's crafting recipes
 * are Assembling recipes at their own ids, shaped ones read as bags; special recipes and transmutes stay
 * at the crafting table; and with the flag off, vanilla's recipe is back as it was.
 */
final class VanillaPackTests {

    /** Shaped: two planks in a column make four sticks. */
    private static final String STICK = "minecraft:stick";
    /** Shapeless. */
    private static final String FLINT_AND_STEEL = "minecraft:flint_and_steel";
    /** Special: no fixed ingredients or result. */
    private static final String FIREWORK_ROCKET = "minecraft:firework_rocket";
    /** A transmute: the result keeps the input's components, so it has no fixed result either. */
    private static final String BLACK_SHULKER_BOX = "minecraft:black_shulker_box";

    private VanillaPackTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("the_vanilla_pack_replaces_vanilla_crafting_at_its_ids", 20, VanillaPackTests::replaces);
        tests.test("special_recipes_stay_at_the_crafting_table", 20, VanillaPackTests::specialsStay);
    }

    static void registerReloading(CraftworksGameTests.Registrar tests) {
        tests.test("with_vanilla_recipes_off_vanillas_recipe_is_untouched", 400, VanillaPackTests::offLeavesVanilla);
    }

    private static void replaces(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String id : List.of(STICK, FLINT_AND_STEEL)) {
            if (type(server, id) != CraftworksRecipes.ASSEMBLING_TYPE.get()) {
                helper.fail(id + " should be the vanilla pack's Assembling recipe, and is " + type(server, id));
                return;
            }
        }
        AssemblingRecipe stick = RuntimeAssemblingRecipes.recipes(helper.getLevel()).byId(STICK);
        if (stick == null || stick.result().count() != 4 || stick.ingredients().size() != 1) {
            helper.fail(STICK + " should be one ingredient making four sticks, and is " + stick);
            return;
        }
        Ingredient planks = stick.ingredients().getFirst();
        if (planks.count() != 2 || !planks.items().contains("minecraft:oak_planks")) {
            helper.fail("the stick's two-slot column of planks should be a bag of two planks, and is " + planks);
            return;
        }
        AssemblingRecipe flint = RuntimeAssemblingRecipes.recipes(helper.getLevel()).byId(FLINT_AND_STEEL);
        if (flint == null || flint.ingredients().size() != 2) {
            helper.fail(FLINT_AND_STEEL + " should take an iron ingot and a flint, and is " + flint);
            return;
        }
        helper.succeed();
    }

    private static void specialsStay(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String id : List.of(FIREWORK_ROCKET, BLACK_SHULKER_BOX)) {
            if (type(server, id) != RecipeType.CRAFTING) {
                helper.fail(id + " has no fixed ingredients and result and should stay minecraft:crafting, and is "
                        + type(server, id));
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Turns the flag off, waits for the reload, reads the stick, and turns it back on before passing or
     * failing, since the rest of the run expects the pack.
     */
    private static void offLeavesVanilla(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AtomicReference<String> seen = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        boolean before = CraftworksConfig.VANILLA_RECIPES.get();
        CraftworksConfig.VANILLA_RECIPES.set(false);
        VanillaPack.follow(server)
                .thenRun(() -> seen.set(String.valueOf(type(server, STICK))))
                .whenComplete((ignored, thrown) -> {
                    if (thrown != null) error.set(thrown);
                    CraftworksConfig.VANILLA_RECIPES.set(before);
                })
                .thenCompose(ignored -> VanillaPack.follow(server))
                .whenComplete((ignored, thrown) -> {
                    if (thrown != null) error.compareAndSet(null, thrown);
                    if (seen.get() == null) seen.set("nothing");
                });
        helper.succeedWhen(() -> {
            if (error.get() != null) helper.fail("the reload failed: " + error.get());
            String off = seen.get();
            helper.assertTrue(off != null && type(server, STICK) == CraftworksRecipes.ASSEMBLING_TYPE.get(),
                    "waiting for the reloads to finish");
            if (!off.equals(String.valueOf(RecipeType.CRAFTING))) {
                helper.fail("with vanillaRecipes off, " + STICK + " should be vanilla's minecraft:crafting, and is " + off);
            }
        });
    }

    private static RecipeType<?> type(MinecraftServer server, String id) {
        return server.getRecipeManager().recipeMap().values().stream()
                .filter(holder -> holder.id().identifier().toString().equals(id))
                .map(holder -> (RecipeType<?>) holder.value().getType())
                .findFirst().orElse(null);
    }
}
