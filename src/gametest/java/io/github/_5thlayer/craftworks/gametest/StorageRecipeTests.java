// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.assembler.FillRequest;
import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.github._5thlayer.craftworks.planner.AssemblingRecipe;
import io.github._5thlayer.craftworks.recipe.RuntimeAssemblingRecipes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Storage recipes (#78, GLOSSARY.md): the Converted recipes that pack an item into its block or unpack it take
 * one tick, in the vanilla pack the build writes and in other mods' recipes converted at load; a one-way
 * recipe, or one whose way back makes another count, keeps the default; a recipe a pack writes as an
 * Assembling recipe keeps its own time. The runtime cases are the {@code storage_recipes} pack's crafting recipes.
 */
final class StorageRecipeTests {

    private static final String PACK = "storage_recipes";
    private static final int STORAGE_TIME = io.github._5thlayer.craftworks.recipe.AssemblingRecipe.STORAGE_TIME;
    private static final int DEFAULT_TIME = io.github._5thlayer.craftworks.recipe.AssemblingRecipe.DEFAULT_TIME;

    private StorageRecipeTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("storage_recipe_vanilla_packs_and_unpacks_take_one_tick", 20, StorageRecipeTests::vanillaStorage);
        tests.test("storage_recipe_vanilla_one_way_recipes_keep_the_default", 20, StorageRecipeTests::vanillaOneWay);
        tests.test("storage_recipe_a_plan_of_iron_blocks_takes_a_tick_a_block", 100, StorageRecipeTests::ironBlocksPlan);
        var packed = tests.withPack(PACK);
        packed.test("storage_recipe_pack_round_trip_takes_one_tick_both_ways", 20, StorageRecipeTests::packRoundTrip);
        packed.test("storage_recipe_pack_tagged_round_trip_takes_one_tick_both_ways", 20, StorageRecipeTests::packTaggedRoundTrip);
        packed.test("storage_recipe_pack_one_way_and_mismatched_counts_keep_the_default", 20, StorageRecipeTests::packNotStorage);
        packed.test("storage_recipe_pack_assembling_recipes_keep_their_own_time", 20, StorageRecipeTests::packOwnTime);
    }

    private static void vanillaStorage(GameTestHelper helper) {
        check(helper, STORAGE_TIME, "minecraft:iron_block", "minecraft:iron_ingot_from_iron_block",
                "minecraft:iron_nugget", "minecraft:iron_ingot_from_nuggets", "minecraft:redstone_block", "minecraft:redstone",
                "minecraft:lapis_block", "minecraft:lapis_lazuli", "minecraft:coal_block", "minecraft:coal",
                "minecraft:hay_block", "minecraft:wheat", "minecraft:bone_block", "minecraft:bone_meal_from_bone_block", "minecraft:slime_block", "minecraft:slime_ball");
        helper.succeed();
    }

    /** No way back: the table, sandstone, wool and honey. */
    private static void vanillaOneWay(GameTestHelper helper) {
        check(helper, DEFAULT_TIME, "minecraft:crafting_table", "minecraft:sandstone", "minecraft:white_wool_from_string",
                "minecraft:honey_block", "minecraft:stick");
        helper.succeed();
    }

    /** Nine ingots a block, a tick a block: N blocks arrive after N ticks of the player's crafting. */
    private static void ironBlocksPlan(GameTestHelper helper) {
        int blocks = 5;
        ServerPlayer player = AssemblerTests.playerHolding(helper, new ItemStack(Items.IRON_INGOT, 9 * blocks));
        PersonalAssembler.fill(player, Identifier.parse("minecraft:iron_block"), FillRequest.ALL);
        helper.assertTrue(PersonalAssembler.queueOf(player).entries().stream().mapToInt(row -> row.remainingRootCrafts()).sum() == blocks,
                "the plan should queue " + blocks + " crafts, and queues " + PersonalAssembler.queueOf(player).entries());
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (AssemblerTests.count(player, Items.IRON_BLOCK) < blocks) {
                ticks[0]++;
                EventHooks.firePlayerTickPost(player);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(AssemblerTests.count(player, Items.IRON_BLOCK) == blocks, "no " + blocks + " iron blocks yet");
            helper.assertTrue(ticks[0] <= blocks + 1, blocks + " iron blocks took " + ticks[0] + " ticks, not about " + blocks);
        });
    }

    private static void packRoundTrip(GameTestHelper helper) {
        check(helper, STORAGE_TIME, "craftworks:storage_pack", "craftworks:storage_unpack");
        helper.succeed();
    }

    private static void packTaggedRoundTrip(GameTestHelper helper) {
        check(helper, STORAGE_TIME, "craftworks:storage_tag_pack", "craftworks:storage_tag_unpack");
        helper.succeed();
    }

    private static void packNotStorage(GameTestHelper helper) {
        check(helper, DEFAULT_TIME, "craftworks:storage_one_way", "craftworks:storage_wrong_count",
                "craftworks:storage_wrong_count_back");
        helper.succeed();
    }

    /** The pack's own Assembling recipes at vanilla's storage ids: 7 where it sets 7, the default where it sets none. */
    private static void packOwnTime(GameTestHelper helper) {
        check(helper, 7, "minecraft:iron_ingot_from_iron_block");
        check(helper, DEFAULT_TIME, "minecraft:iron_nugget");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, int time, String... ids) {
        var recipes = RuntimeAssemblingRecipes.recipes(helper.getLevel());
        for (String id : ids) {
            AssemblingRecipe recipe = recipes.byId(id);
            if (recipe == null) {
                throw helper.assertionException(id + " should be an Assembling recipe, and is not loaded");
            }
            if (recipe.time() != time) {
                throw helper.assertionException(id + " should take " + time + " tick(s), and takes " + recipe.time());
            }
        }
    }
}
