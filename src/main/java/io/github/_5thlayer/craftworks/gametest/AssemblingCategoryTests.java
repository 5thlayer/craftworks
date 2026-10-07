// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.gametest.AssemblerMachineTests.Placed;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.MachineState;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.HoldVerdict;
import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import io.github._5thlayer.craftworks.recipe.RuntimeAssemblingRecipes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * An Assembling recipe's category and its results list (#24): the codec, the tier's categories at Fill
 * Recipe and under Fast Replace, what a craft with several results puts in the two outputs, and that
 * only a recipe with exactly one item result is Hand-craftable. The recipes are the game tests' own, but for {@code dev_pack/}'s, which only the dev client loads.
 */
final class AssemblingCategoryTests {

    private static final Identifier CATEGORY_FLUID = id("gametest/category_fluid");
    private static final Identifier FLUID_ONLY = id("gametest/fluid_only");
    private static final Identifier TWO_RESULTS = id("gametest/two_results");
    private static final Identifier CLASH = id("gametest/results_and_remainders_clash");
    private static final Identifier JOIN = id("gametest/results_and_remainders_join");
    /** A config that leaves out the fluid category, as an existing world's may. */
    private static final List<AssemblingCategory> WITHOUT_FLUID = List.of(AssemblingCategory.CRAFTING, AssemblingCategory.ADVANCED_CRAFTING);

    private AssemblingCategoryTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, path);
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("the_codec_reads_category_and_results_and_refuses_an_empty_results_with_no_fluid", 20, AssemblingCategoryTests::codec);
        tests.test("every_tier_holds_all_three_categories_by_default", 20, AssemblingCategoryTests::configDefaults);
        tests.test("every_tier_takes_a_crafting_with_fluid_recipe_at_fill_recipe", 20, AssemblingCategoryTests::everyTierTakes);
        tests.test("a_tier_whose_config_leaves_out_crafting_with_fluid_refuses_such_a_recipe_at_fill_recipe", 20, AssemblingCategoryTests::restrictedRefuses);
        tests.test("a_fluid_only_recipe_is_refused_by_category_on_a_tier_whose_config_leaves_it_out", 20, AssemblingCategoryTests::fluidOnly);
        tests.test("a_fast_replace_to_a_tier_without_the_category_leaves_the_recipe_held_and_idle", 20, AssemblingCategoryTests::fastReplace);
        tests.test("a_craft_with_two_item_results_fills_the_product_and_remainder_slots", 20, AssemblingCategoryTests::twoResults);
        tests.test("an_extra_result_joins_the_remainders_and_a_different_one_is_refused", 20, AssemblingCategoryTests::extraResultsAndRemainders);
        tests.test("a_recipe_with_two_item_results_is_not_hand_craftable", 20, AssemblingCategoryTests::notHandCraftable);
        tests.test("a_fluid_only_recipe_may_leave_out_its_empty_item_lists", 20, AssemblingCategoryTests::noItemLists);
        tests.test("a_recipe_naming_a_removed_category_fails_to_load_and_points_to_crafting_with_fluid", 20,
                AssemblingCategoryTests::removedCategories);
        tests.test("every_dev_pack_recipe_parses", 20, AssemblingCategoryTests::devPack);
    }

    // -- the codec ------------------------------------------------------------------------------

    private static DataResult<AssemblingRecipe> read(GameTestHelper helper, String json) {
        JsonElement element = JsonParser.parseString(json);
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        return CraftworksRecipes.ASSEMBLING_SERIALIZER.get().codec().codec().parse(ops, element);
    }

    /** A recipe that uses and makes only fluids: it names neither {@code ingredients} nor {@code results}. */
    private static void noItemLists(GameTestHelper helper) {
        AssemblingRecipe recipe = read(helper, "{ \"category\": \"crafting-with-fluid\", \"fluid_ingredients\": ["
                + "{ \"ingredient\": \"minecraft:water\", \"amount\": 100 }], \"fluid_results\": ["
                + "{ \"id\": \"minecraft:lava\", \"amount\": 30 }] }").getOrThrow();
        helper.assertTrue(recipe.ingredients().isEmpty() && recipe.results().isEmpty(),
                "left out, ingredients and results read as " + recipe.ingredients() + " and " + recipe.results());
        helper.assertTrue(read(helper, "{ \"fluid_ingredients\": [{ \"ingredient\": \"minecraft:water\", \"amount\": 100 }] }")
                .isError(), "a recipe that makes nothing, no item and no fluid, read as a recipe");
        helper.succeed();
    }

    /** {@code chemistry} and {@code oil-processing} were removed in 0.6: a recipe naming either is refused, and the error says what to use. */
    private static void removedCategories(GameTestHelper helper) {
        for (String removed : List.of("chemistry", "oil-processing")) {
            var result = read(helper, "{ \"category\": \"" + removed + "\", \"fluid_results\": ["
                    + "{ \"id\": \"minecraft:lava\", \"amount\": 30 }] }");
            helper.assertTrue(result.isError(), "a recipe naming the removed category " + removed + " read as a recipe");
            String message = result.error().orElseThrow().message();
            helper.assertTrue(message.contains("'" + removed + "'") && message.contains("crafting-with-fluid"),
                    "the error for " + removed + " does not point to crafting-with-fluid: " + message);
        }
        helper.succeed();
    }

    /**
     * The dev client's own recipes ({@code dev_pack/}), which no other run loads: each parses, so one that the codec
     * refuses fails the build and not only the dev client's log.
     */
    private static void devPack(GameTestHelper helper) {
        // Read off the mod's own content roots: the module's class loader hides a folder that holds no class. A root
        // is a folder in every run that has game tests; from a jar this finds nothing and fails, not passes.
        var roots = ModList.get().getModFileById(Craftworks.MOD_ID).getFile().getContents().getContentRoots();
        List<Path> files = new ArrayList<>();
        for (var contentRoot : roots) {
            Path recipes = contentRoot.resolve("dev_pack/data/craftworks/recipe");
            if (!Files.isDirectory(recipes)) {
                continue;
            }
            try (var walk = Files.walk(recipes)) {
                walk.filter(path -> path.toString().endsWith(".json")).sorted().forEach(files::add);
            } catch (IOException e) {
                helper.fail("could not list dev_pack's recipes under " + recipes + ": " + e);
                return;
            }
        }
        if (files.isEmpty()) {
            helper.fail("no dev_pack recipes in any of the mod's content roots " + roots);
            return;
        }
        for (var file : files) {
            DataResult<AssemblingRecipe> result;
            try {
                result = read(helper, Files.readString(file));
            } catch (IOException e) {
                helper.fail("could not read " + file + ": " + e);
                return;
            }
            if (result.isError()) {
                helper.fail(file.getFileName() + " does not parse: " + result.error().orElseThrow().message());
                return;
            }
        }
        helper.succeed();
    }

    private static void codec(GameTestHelper helper) {
        String ingredients = "\"ingredients\": [{ \"ingredient\": \"minecraft:iron_ingot\", \"count\": 1 }]";
        AssemblingRecipe two = read(helper, "{" + ingredients + ", \"category\": \"advanced-crafting\", \"results\": ["
                + "{ \"id\": \"minecraft:gold_nugget\", \"count\": 3 }, \"minecraft:stick\"] }").getOrThrow();
        helper.assertTrue(two.category() == AssemblingCategory.ADVANCED_CRAFTING, "the category read as " + two.category());
        helper.assertTrue(two.results().size() == 2 && two.results().getFirst().count() == 3 && two.results().get(1).count() == 1,
                "the results read as " + two.results());

        AssemblingRecipe plain = read(helper, "{" + ingredients + ", \"results\": [\"minecraft:stick\"] }").getOrThrow();
        helper.assertTrue(plain.category() == AssemblingCategory.CRAFTING, "an omitted category read as " + plain.category());

        AssemblingRecipe fluid = read(helper, "{" + ingredients + ", \"results\": [], \"fluid_results\": ["
                + "{ \"id\": \"minecraft:lava\", \"amount\": 50 }] }").getOrThrow();
        helper.assertTrue(fluid.results().isEmpty() && fluid.fluidResults().size() == 1, "an empty results with a fluid result read as " + fluid);

        helper.assertTrue(read(helper, "{" + ingredients + ", \"results\": [] }").isError(), "an empty results with no fluid result was read");
        helper.assertTrue(read(helper, "{" + ingredients + ", \"results\": [], \"fluid_results\": [] }").isError(),
                "an empty results with an empty fluid_results was read");
        var unknown = read(helper, "{" + ingredients + ", \"category\": \"advanced_crafting\", \"results\": [\"minecraft:stick\"] }");
        helper.assertTrue(unknown.isError() && unknown.error().orElseThrow().message().contains("advanced_crafting"),
                "an unknown category was read, or its error does not name it: " + unknown);
        helper.assertTrue(read(helper, "{" + ingredients + ", \"result\": \"minecraft:stick\" }").isError(),
                "the removed result was still read");
        helper.succeed();
    }

    // -- categories -----------------------------------------------------------------------------

    private static void configDefaults(GameTestHelper helper) {
        List<AssemblingCategory> all = List.of(AssemblingCategory.CRAFTING, AssemblingCategory.ADVANCED_CRAFTING,
                AssemblingCategory.CRAFTING_WITH_FLUID);
        for (AssemblerTier tier : AssemblerTier.values()) {
            helper.assertTrue(CraftworksConfig.categories(tier).equals(all), tier + " takes " + CraftworksConfig.categories(tier));
        }
        helper.succeed();
    }

    private static void restrictedRefuses(GameTestHelper helper) {
        AssemblerMachineTests.withCategories(AssemblerTier.ONE, WITHOUT_FLUID, () -> {
            Placed one = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
            HoldVerdict refused = AssemblerMachineTests.request(one, CATEGORY_FLUID);
            helper.assertTrue(refused == HoldVerdict.WRONG_CATEGORY, "a tier 1 restricted to crafting answered a crafting-with-fluid recipe with " + refused);
            helper.assertTrue(one.machine().heldRecipe().isEmpty(), "a refused recipe was held");
            helper.assertTrue(one.player().heard.contains("craftworks.assembler.refused.wrong_category"),
                    "the player was told " + one.player().heard);
        });
        helper.succeed();
    }

    /** The default config: each tier takes the category, and a fluid in the recipe is a separate refusal (#25). */
    private static void everyTierTakes(GameTestHelper helper) {
        for (AssemblerTier tier : AssemblerTier.values()) {
            Placed assembler = AssemblerMachineTests.place(helper, tier);
            HoldVerdict taken = AssemblerMachineTests.request(assembler, CATEGORY_FLUID);
            helper.assertTrue(taken == HoldVerdict.HELD, tier + " answered a crafting-with-fluid recipe with " + taken);
            helper.destroyBlock(AssemblerMachineTests.ORIGIN);
        }
        helper.succeed();
    }

    private static void fluidOnly(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        helper.assertTrue(AssemblerMachineTests.request(assembler, FLUID_ONLY) == HoldVerdict.HELD,
                "tier 1 refused a crafting-with-fluid recipe with no item result by default");
        helper.destroyBlock(AssemblerMachineTests.ORIGIN);
        AssemblerMachineTests.withCategories(AssemblerTier.ONE, WITHOUT_FLUID, () -> {
            Placed restricted = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
            HoldVerdict verdict = AssemblerMachineTests.request(restricted, FLUID_ONLY);
            helper.assertTrue(verdict == HoldVerdict.WRONG_CATEGORY, "tier 1 answered a crafting-with-fluid recipe with no item result with " + verdict);
        });
        helper.succeed();
    }

    private static void fastReplace(GameTestHelper helper) {
        AssemblerMachineTests.withCategories(AssemblerTier.ONE, WITHOUT_FLUID, () -> fastReplaceToRestrictedTier1(helper));
        helper.succeed();
    }

    private static void fastReplaceToRestrictedTier1(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        helper.assertTrue(AssemblerMachineTests.request(assembler, CATEGORY_FLUID) == HoldVerdict.HELD, "tier 2 refused a crafting-with-fluid recipe");
        AssemblerMachineTests.insert(assembler, 0, Items.IRON_INGOT, 1);
        helper.assertTrue(assembler.machine().state() != MachineState.CANT_RUN, "tier 2 could not run it");

        AssemblerMachineTests.swap(helper, assembler, AssemblerTier.ONE, AssemblerMachineTests.ORIGIN);
        helper.assertTrue(assembler.machine().heldRecipe().equals(java.util.Optional.of(CATEGORY_FLUID)), "the swap lost the Held recipe");
        helper.assertTrue(assembler.machine().state() == MachineState.CANT_RUN, "tier 1 state was " + assembler.machine().state());
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int tick = 0; tick < 60; tick++) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 0, "tier 1 crafted a recipe outside its categories");
        helper.assertTrue(AssemblerMachineTests.count(assembler, 0) == 1, "tier 1 spent the input");
    }

    // -- results --------------------------------------------------------------------------------

    private static void twoResults(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, TWO_RESULTS);
        AssemblerMachineTests.insert(assembler, 0, Items.IRON_INGOT, 1);
        run(helper, assembler, 20);
        helper.assertTrue(stack(assembler, AssemblerSlots.PRODUCT, Items.GOLD_NUGGET, 3), "the product slot holds " + describe(assembler, AssemblerSlots.PRODUCT));
        helper.assertTrue(stack(assembler, AssemblerSlots.REMAINDERS, Items.STICK, 2), "the remainder slot holds " + describe(assembler, AssemblerSlots.REMAINDERS));
        helper.succeed();
    }

    private static void extraResultsAndRemainders(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        HoldVerdict clash = AssemblerMachineTests.request(assembler, CLASH);
        helper.assertTrue(clash == HoldVerdict.REMAINDERS_DONT_FIT, "a stick and a bucket left behind was " + clash);
        helper.assertTrue(assembler.player().heard.contains("craftworks.assembler.refused.remainders_dont_fit"),
                "the player was told " + assembler.player().heard);

        AssemblerMachineTests.hold(assembler, JOIN);
        AssemblerMachineTests.insert(assembler, 0, Items.MILK_BUCKET, 1);
        run(helper, assembler, 20);
        helper.assertTrue(stack(assembler, AssemblerSlots.PRODUCT, Items.SLIME_BALL, 2), "the product slot holds " + describe(assembler, AssemblerSlots.PRODUCT));
        helper.assertTrue(stack(assembler, AssemblerSlots.REMAINDERS, Items.BUCKET, 2),
                "the extra bucket and the milk's own did not share the remainder slot: " + describe(assembler, AssemblerSlots.REMAINDERS));
        helper.succeed();
    }

    private static void notHandCraftable(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().recipeMap()
                .byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, TWO_RESULTS)) != null,
                "the recipe with two item results did not load");
        var recipes = RuntimeAssemblingRecipes.recipes(helper.getLevel());
        helper.assertTrue(recipes.byId(TWO_RESULTS.toString()) == null, "a recipe with two item results is in the Crafting Plan's set");
        helper.assertTrue(recipes.byId(FLUID_ONLY.toString()) == null, "a recipe with no item result is in the Crafting Plan's set");
        helper.assertTrue(recipes.byId(AssemblerTests.OAK_SAPLING.toString()) != null, "a recipe with one item result is not");
        helper.succeed();
    }

    // -- helpers --------------------------------------------------------------------------------

    private static void run(GameTestHelper helper, Placed assembler, int ticks) {
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int tick = 0; tick < ticks; tick++) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
    }

    private static boolean stack(Placed assembler, int slot, Item item, int count) {
        return assembler.machine().inventory().getResource(slot).getItem() == item && AssemblerMachineTests.count(assembler, slot) == count;
    }

    private static String describe(Placed assembler, int slot) {
        return AssemblerMachineTests.count(assembler, slot) + " of " + assembler.machine().inventory().getResource(slot).getItem();
    }
}
