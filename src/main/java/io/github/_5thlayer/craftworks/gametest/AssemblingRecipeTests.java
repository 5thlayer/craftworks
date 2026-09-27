// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;

import io.github._5thlayer.craftworks.planner.AssemblingRecipe;
import io.github._5thlayer.craftworks.planner.AssemblingRecipeSet;
import io.github._5thlayer.craftworks.planner.Ingredient;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import io.github._5thlayer.craftworks.recipe.RuntimeAssemblingRecipes;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * That Assembling recipes are read off {@code craftworks:assembling} as the server loaded them, adapted
 * from PlanetaryFactory's HandSetTests.
 *
 * <p>The glue no unit test reaches: that the recipe manager holds a datapack's recipes under the type,
 * that {@link RuntimeAssemblingRecipes} finds them there, that a recipe of another type stays out, that a tag ingredient arrives as the items it
 * names, that {@code time} and {@code priority} default when left out, and that a spent item's remainder
 * rides on its ingredient. The recipes are the dev runs' datapack's ({@code dev_pack/}).
 */
final class AssemblingRecipeTests {

    private static final String OAK_SAPLING = "craftworks:gametest/oak_sapling";
    private static final String OAK_PLANKS = "minecraft:oak_planks";
    private static final String FLINT_AND_STEEL = "minecraft:flint_and_steel";
    private static final String SLIME_BALL = "craftworks:gametest/slime_ball";

    private AssemblingRecipeTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("assembling_recipes_are_read_off_their_type", 20, AssemblingRecipeTests::readOffTheirType);
        tests.test("assembling_recipe_fields_default", 20, AssemblingRecipeTests::fieldsDefault);
        tests.test("an_ingredient_carries_its_remainder", 20, AssemblingRecipeTests::remainder);
        tests.test("an_assembling_recipe_replaces_vanillas_at_its_id", 20, AssemblingRecipeTests::replacesVanilla);
    }

    private static void readOffTheirType(GameTestHelper helper) {
        AssemblingRecipeSet recipes = RuntimeAssemblingRecipes.recipes(helper.getLevel());
        AssemblingRecipe sapling = recipes.byId(OAK_SAPLING);
        if (sapling == null) {
            helper.fail("the Assembling recipe set holds " + recipes.size() + " recipe(s) and not " + OAK_SAPLING);
            return;
        }
        Ingredient logs = sapling.ingredients().getFirst();
        if (!logs.items().contains("minecraft:oak_log") || logs.count() != 2) {
            helper.fail(OAK_SAPLING + " takes " + logs + "; #minecraft:oak_logs should arrive as the logs it names, two of them");
            return;
        }
        if (!recipes.routes("minecraft:oak_sapling").contains(sapling)) {
            helper.fail("the set does not know " + OAK_SAPLING + " makes an oak sapling");
            return;
        }
        // Loaded, but vanilla's crafting type: the Assembler plans with its own type and no other.
        boolean loaded = helper.getLevel().getServer().getRecipeManager().recipeMap().values().stream()
                .anyMatch(holder -> holder.id().identifier().toString().equals(OAK_PLANKS));
        if (!loaded) {
            helper.fail(OAK_PLANKS + " is not in the recipe manager, so its absence below proves nothing");
            return;
        }
        if (recipes.byId(OAK_PLANKS) != null) {
            helper.fail(OAK_PLANKS + " is a minecraft:crafting recipe and is in the Assembling recipe set");
            return;
        }
        helper.succeed();
    }

    private static void fieldsDefault(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getRecipeManager();
        var byType = manager.recipeMap().byType(CraftworksRecipes.ASSEMBLING_TYPE.get());
        io.github._5thlayer.craftworks.recipe.AssemblingRecipe sapling = null;
        io.github._5thlayer.craftworks.recipe.AssemblingRecipe slime = null;
        for (var holder : byType) {
            String id = holder.id().identifier().toString();
            if (id.equals(OAK_SAPLING)) sapling = holder.value();
            if (id.equals(SLIME_BALL)) slime = holder.value();
        }
        if (sapling == null || slime == null) {
            helper.fail("the recipe manager holds neither or not both of " + List.of(OAK_SAPLING, SLIME_BALL)
                    + " under craftworks:assembling");
            return;
        }
        if (sapling.time() != 10 || sapling.priority() != 0) {
            helper.fail(OAK_SAPLING + " leaves time and priority out, and reads as " + sapling.time() + " ticks, priority "
                    + sapling.priority() + "; 10 and 0 are the defaults");
            return;
        }
        if (slime.time() != 40 || slime.priority() != 3 || slime.result().count() != 4) {
            helper.fail(SLIME_BALL + " reads as " + slime + "; its file says 40 ticks, priority 3, four slime balls");
            return;
        }
        helper.succeed();
    }

    private static void remainder(GameTestHelper helper) {
        AssemblingRecipe slime = RuntimeAssemblingRecipes.recipes(helper.getLevel()).byId(SLIME_BALL);
        if (slime == null) {
            helper.fail(SLIME_BALL + " is not in the Assembling recipe set");
            return;
        }
        String left = slime.ingredients().getFirst().remainder("minecraft:milk_bucket");
        if (!"minecraft:bucket".equals(left)) {
            helper.fail("a spent milk bucket should leave a bucket, and " + SLIME_BALL + " says " + left);
            return;
        }
        if (slime.ingredients().get(1).remainder("minecraft:lime_dye") != null) {
            helper.fail("lime dye leaves nothing, and " + SLIME_BALL + " says it does");
            return;
        }
        helper.succeed();
    }

    /**
     * A datapack recipe at vanilla's id, of the Assembling type, replaces vanilla's shapeless one: the
     * shape the built-in vanilla pack will take.
     */
    private static void replacesVanilla(GameTestHelper helper) {
        var holder = helper.getLevel().getServer().getRecipeManager().recipeMap().values().stream()
                .filter(recipe -> recipe.id().identifier().toString().equals(FLINT_AND_STEEL))
                .findFirst();
        if (holder.isEmpty() || holder.get().value().getType() != CraftworksRecipes.ASSEMBLING_TYPE.get()) {
            helper.fail(FLINT_AND_STEEL + " should be the dev pack's Assembling recipe, and is "
                    + holder.map(recipe -> recipe.value().getType().toString()).orElse("missing"));
            return;
        }
        AssemblingRecipe flint = RuntimeAssemblingRecipes.recipes(helper.getLevel()).byId(FLINT_AND_STEEL);
        if (flint == null || !flint.result().item().equals("minecraft:flint_and_steel")) {
            helper.fail(FLINT_AND_STEEL + " is not in the Assembling recipe set as a flint and steel");
            return;
        }
        helper.succeed();
    }
}
