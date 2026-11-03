// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.assembler.FillRequest;
import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.github._5thlayer.craftworks.planner.AssemblingRecipe;
import io.github._5thlayer.craftworks.recipe.ModRecipes;
import io.github._5thlayer.craftworks.recipe.RuntimeAssemblingRecipes;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Storage recipes (#78, GLOSSARY.md): the Converted recipes that pack an item into its block or unpack it take
 * one tick, in the vanilla pack the build writes and in other mods' recipes converted at load; a one-way
 * recipe, or one whose way back makes another count, keeps the default; a recipe a pack writes as an
 * Assembling recipe keeps its own time. The runtime cases are the {@code storage_recipes} pack's crafting recipes.
 *
 * <p>The {@code storage_crafting_} tests are the Storage crafting recipes (#83): another mod that packs and unpacks
 * items asks the crafting recipes the vanilla way and finds the way up and down, shaped for the way up, while a
 * crafting recipe that is no Storage recipe still finds nothing; {@code modRecipesExcluded} can name one away.
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
        packed.test("storage_recipe_a_replaced_direction_leaves_the_vanilla_other_one_at_the_default", 20, StorageRecipeTests::packReplaced);
        packed.test("storage_recipe_a_pack_recipe_and_a_vanilla_pack_recipe_make_a_round_trip", 20, StorageRecipeTests::mixedOrigins);
        tests.test("storage_crafting_nine_iron_ingots_craft_an_iron_block_and_back", 20, StorageRecipeTests::ironRoundTrip);
        tests.test("storage_crafting_the_way_up_is_a_shaped_recipe", 20, StorageRecipeTests::wayUpIsShaped);
        tests.test("storage_crafting_a_recipe_that_is_no_storage_recipe_finds_nothing", 20, StorageRecipeTests::notStorage);
    }

    static void registerReloading(CraftworksGameTests.Registrar tests) {
        tests.test("storage_crafting_recipes_are_excluded_by_id", 600, StorageRecipeTests::excluded);
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

    /**
     * The pack replaced the ways back from the iron block and from the ingot with its own Assembling recipes, so no
     * Converted recipe undoes vanilla's iron block or nugget-to-ingot any more: they take the default.
     */
    private static void packReplaced(GameTestHelper helper) {
        check(helper, DEFAULT_TIME, "minecraft:iron_block", "minecraft:iron_ingot_from_nuggets");
        helper.succeed();
    }

    /** The pack's four planks to a log and the vanilla pack's log to four planks undo each other. */
    private static void mixedOrigins(GameTestHelper helper) {
        check(helper, STORAGE_TIME, "craftworks:storage_planks_to_log", "minecraft:oak_planks");
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

    private static final String IRON_BLOCK_CRAFTING = "craftworks:storage/minecraft/iron_block";

    /** What a crafting grid of {@code side} by {@code side} of the item makes, asked the vanilla way. */
    private static Optional<RecipeHolder<CraftingRecipe>> crafting(GameTestHelper helper, Item item, int side) {
        var input = CraftingInput.of(side, side, Collections.nCopies(side * side, new ItemStack(item)));
        return helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    }

    private static void makes(GameTestHelper helper, Item item, int side, Item result, int count) {
        var found = crafting(helper, item, side);
        helper.assertTrue(found.isPresent(), side + "x" + side + " of " + item + " finds no crafting recipe");
        ItemStack made = found.get().value().assemble(CraftingInput.of(side, side, Collections.nCopies(side * side, new ItemStack(item))));
        helper.assertTrue(made.is(result) && made.getCount() == count,
                side + "x" + side + " of " + item + " should make " + count + " " + result + ", and makes " + made);
    }

    private static void ironRoundTrip(GameTestHelper helper) {
        makes(helper, Items.IRON_INGOT, 3, Items.IRON_BLOCK, 1);
        makes(helper, Items.IRON_BLOCK, 1, Items.IRON_INGOT, 9);
        makes(helper, Items.IRON_NUGGET, 3, Items.IRON_INGOT, 1);
        makes(helper, Items.IRON_INGOT, 1, Items.IRON_NUGGET, 9);
        helper.succeed();
    }

    /** Compacting drawers take only a shaped recipe for the way down from a block, so the way up is one. */
    private static void wayUpIsShaped(GameTestHelper helper) {
        var holder = at(helper.getLevel().getServer(), IRON_BLOCK_CRAFTING);
        helper.assertTrue(holder != null && holder.value() instanceof ShapedRecipe,
                IRON_BLOCK_CRAFTING + " should be a shaped crafting recipe, and is " + (holder == null ? "missing" : holder.value()));
        helper.succeed();
    }

    /** Four planks make a crafting table, and nothing makes the planks back: no Storage recipe, no crafting recipe. */
    private static void notStorage(GameTestHelper helper) {
        var found = crafting(helper, Items.OAK_PLANKS, 2);
        helper.assertTrue(found.isEmpty(), "four oak planks should find no crafting recipe, and find " + found.map(RecipeHolder::id));
        helper.succeed();
    }

    private static void excluded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean();
        List<? extends String> excludedBefore = CraftworksConfig.MOD_RECIPES_EXCLUDED.get();
        String ingot = "craftworks:storage/minecraft/iron_ingot_from_iron_block";
        CraftworksConfig.MOD_RECIPES_EXCLUDED.set(List.of(IRON_BLOCK_CRAFTING));
        ModRecipes.follow(server)
                .thenRun(() -> {
                    if (at(server, IRON_BLOCK_CRAFTING) != null || at(server, ingot) == null) {
                        failure.compareAndSet(null, "excluding " + IRON_BLOCK_CRAFTING + " should drop it and keep " + ingot
                                + "; they are " + at(server, IRON_BLOCK_CRAFTING) + " and " + at(server, ingot));
                    }
                })
                .handle((ignored, thrown) -> {
                    if (thrown != null) failure.compareAndSet(null, "the reload failed: " + thrown);
                    CraftworksConfig.MOD_RECIPES_EXCLUDED.set(excludedBefore);
                    return null;
                })
                .thenCompose(ignored -> ModRecipes.follow(server))
                .thenRun(() -> {
                    if (at(server, IRON_BLOCK_CRAFTING) == null) failure.compareAndSet(null, "no longer excluded, " + IRON_BLOCK_CRAFTING + " did not return");
                })
                .whenComplete((ignored, thrown) -> {
                    if (thrown != null) failure.compareAndSet(null, "the reload failed: " + thrown);
                    done.set(true);
                });
        helper.succeedWhen(() -> {
            helper.assertTrue(done.get(), "waiting for the reloads to finish");
            if (failure.get() != null) helper.fail(failure.get());
        });
    }

    private static RecipeHolder<?> at(MinecraftServer server, String id) {
        return server.getRecipeManager().recipeMap().byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)));
    }
}
