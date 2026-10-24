// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import io.github._5thlayer.craftworks.recipe.ModRecipes;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The brewing stand's mixes as Brewed recipes in the recipe map after a load (#76, ADR-0026): their ids, ingredients,
 * water, results, time and category, the two chains to slowness, the container steps, the brews {@link TestBrews}
 * adds the way another mod would, and the flag and the exclude list that remove them.
 */
final class BrewedRecipesTests {

    static final String NIGHT_VISION = "craftworks:brewing/potion/minecraft/night_vision/minecraft.nether_wart/minecraft.golden_carrot";
    private static final String SLOWNESS = "craftworks:brewing/potion/minecraft/slowness/";
    private static final String BREWING = "craftworks:brewing/";

    private BrewedRecipesTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("brewed_night_vision_sits_at_its_id_with_the_stands_ingredients_water_result_and_time", 20, BrewedRecipesTests::nightVision);
        tests.test("brewed_slowness_has_a_recipe_for_each_chain_that_reaches_it", 20, BrewedRecipesTests::slowness);
        tests.test("brewed_splash_and_lingering_carry_their_gunpowder_and_dragons_breath_at_the_end", 20, BrewedRecipesTests::containers);
        tests.test("brewed_water_alone_is_splash_or_lingering_but_never_plain", 20, BrewedRecipesTests::water);
        tests.test("brewed_recipes_take_no_potion_bottle_and_always_bottles_water_and_a_whole_number_of_steps", 20, BrewedRecipesTests::noPotionsIn);
        tests.test("brewed_recipes_of_a_mods_brewing_recipe_convert_from_water_and_from_awkward", 20, BrewedRecipesTests::modBrews);
        tests.test("brewed_a_brewing_recipe_that_is_not_a_brewing_recipe_is_skipped", 20, BrewedRecipesTests::unlisted);
    }

    static void registerReloading(CraftworksGameTests.Registrar tests) {
        tests.test("brewed_recipes_are_excluded_by_id_and_removed_by_the_flag", 600, BrewedRecipesTests::excludesAndOff);
    }

    /** The Brewed recipe at this id, or null. */
    static AssemblingRecipe at(MinecraftServer server, String id) {
        var holder = server.getRecipeManager().recipeMap().byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)));
        return holder != null && holder.value() instanceof AssemblingRecipe recipe ? recipe : null;
    }

    private static boolean takes(AssemblingRecipe recipe, Item item, int count) {
        return recipe.ingredients().stream().anyMatch(sized -> sized.count() == count && sized.ingredient().test(new ItemStack(item)));
    }

    /** Whether the recipe takes these items and no others, glass bottles aside. */
    private static boolean takesOnly(AssemblingRecipe recipe, Item... items) {
        return recipe.ingredients().size() == items.length + 1 && takes(recipe, Items.GLASS_BOTTLE, 3)
                && List.of(items).stream().allMatch(item -> takes(recipe, item, 1));
    }

    /** Whether it makes three of the potion in the bottle, which stacks to one: as three results. */
    private static boolean makesPotion(AssemblingRecipe recipe, Item bottle, Holder<Potion> potion) {
        if (recipe.results().size() != 3) return false;
        for (ItemStackTemplate result : recipe.results()) {
            var contents = result.create().get(DataComponents.POTION_CONTENTS);
            if (result.item().value() != bottle || result.count() != 1 || contents == null
                    || contents.potion().isEmpty() || !contents.potion().get().is(potion) || !contents.customEffects().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String describe(AssemblingRecipe recipe) {
        return recipe == null ? "missing" : recipe.ingredients() + " + " + recipe.fluidIngredients() + " -> " + recipe.results()
                + " in " + recipe.time() + " ticks, " + recipe.category();
    }

    private static void check(GameTestHelper helper, String id, int time, Item bottle, Holder<Potion> potion, Item... ingredients) {
        AssemblingRecipe recipe = at(helper.getLevel().getServer(), id);
        helper.assertTrue(recipe != null, id + " is not in the recipe map");
        helper.assertTrue(takesOnly(recipe, ingredients), id + " takes " + recipe.ingredients());
        helper.assertTrue(makesPotion(recipe, bottle, potion), id + " makes " + recipe.results());
        helper.assertTrue(recipe.time() == time, id + " takes " + recipe.time() + " ticks, not " + time);
    }

    private static void nightVision(GameTestHelper helper) {
        AssemblingRecipe recipe = at(helper.getLevel().getServer(), NIGHT_VISION);
        helper.assertTrue(recipe != null, "no Brewed recipe at " + NIGHT_VISION);
        helper.assertTrue(takesOnly(recipe, Items.NETHER_WART, Items.GOLDEN_CARROT), "its items are " + describe(recipe));
        helper.assertTrue(recipe.fluidIngredients().size() == 1 && recipe.fluidIngredients().getFirst().amount() == 750,
                "its water is " + recipe.fluidIngredients());
        var water = recipe.fluidIngredients().getFirst().ingredient();
        helper.assertTrue(water.test(new FluidStack(Fluids.WATER, 1)) && !water.test(new FluidStack(Fluids.LAVA, 1)),
                "its fluid is not water alone");
        helper.assertTrue(makesPotion(recipe, Items.POTION, Potions.NIGHT_VISION), "it makes " + recipe.results());
        helper.assertTrue(recipe.time() == 800, "it takes " + recipe.time() + " ticks, not two steps' 800");
        helper.assertTrue(recipe.category() == AssemblingCategory.BREWING, "its category is " + recipe.category());
        helper.assertTrue(!recipe.handCraftable() && recipe.fluidResults().isEmpty() && recipe.priority() == AssemblingRecipe.DEFAULT_PRIORITY,
                "it is " + describe(recipe) + ", hand-craftable " + recipe.handCraftable());
        helper.succeed();
    }

    /** Leaping's and swiftness' both lead to slowness through a fermented spider eye; the id says which. */
    private static void slowness(GameTestHelper helper) {
        check(helper, SLOWNESS + "minecraft.nether_wart/minecraft.rabbit_foot/minecraft.fermented_spider_eye", 1200, Items.POTION,
                Potions.SLOWNESS, Items.NETHER_WART, Items.RABBIT_FOOT, Items.FERMENTED_SPIDER_EYE);
        check(helper, SLOWNESS + "minecraft.nether_wart/minecraft.sugar/minecraft.fermented_spider_eye", 1200, Items.POTION,
                Potions.SLOWNESS, Items.NETHER_WART, Items.SUGAR, Items.FERMENTED_SPIDER_EYE);
        helper.succeed();
    }

    private static void containers(GameTestHelper helper) {
        String chain = "minecraft/night_vision/minecraft.nether_wart/minecraft.golden_carrot/";
        check(helper, BREWING + "splash_potion/" + chain + "minecraft.gunpowder", 1200, Items.SPLASH_POTION, Potions.NIGHT_VISION,
                Items.NETHER_WART, Items.GOLDEN_CARROT, Items.GUNPOWDER);
        check(helper, BREWING + "lingering_potion/" + chain + "minecraft.gunpowder/minecraft.dragon_breath", 1600,
                Items.LINGERING_POTION, Potions.NIGHT_VISION,
                Items.NETHER_WART, Items.GOLDEN_CARROT, Items.GUNPOWDER, Items.DRAGON_BREATH);
        helper.succeed();
    }

    private static void water(GameTestHelper helper) {
        check(helper, BREWING + "splash_potion/minecraft/water/minecraft.gunpowder", 400, Items.SPLASH_POTION, Potions.WATER, Items.GUNPOWDER);
        check(helper, BREWING + "lingering_potion/minecraft/water/minecraft.gunpowder/minecraft.dragon_breath", 800,
                Items.LINGERING_POTION, Potions.WATER, Items.GUNPOWDER, Items.DRAGON_BREATH);
        helper.assertTrue(at(helper.getLevel().getServer(), BREWING + "potion/minecraft/water") == null, "plain water has a recipe");
        helper.succeed();
    }

    /** No potion goes in and no potion is a fluid: every recipe is bottles and water and its steps' items. */
    private static void noPotionsIn(GameTestHelper helper) {
        int seen = 0;
        for (var holder : helper.getLevel().getServer().getRecipeManager().recipeMap().values()) {
            if (!holder.id().identifier().getPath().startsWith("brewing/") || !(holder.value() instanceof AssemblingRecipe recipe)) continue;
            seen++;
            String id = holder.id().identifier().toString();
            for (SizedIngredient sized : recipe.ingredients()) {
                for (Item bottle : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
                    helper.assertTrue(!sized.ingredient().test(new ItemStack(bottle)), id + " takes " + bottle);
                }
            }
            helper.assertTrue(recipe.category() == AssemblingCategory.BREWING && !recipe.handCraftable()
                    && recipe.fluidIngredients().size() == 1 && recipe.fluidIngredients().getFirst().amount() == 750
                    && recipe.time() % 400 == 0 && takes(recipe, Items.GLASS_BOTTLE, 3), id + " is " + describe(recipe));
            helper.assertTrue(recipe.ingredients().size() <= AssemblerSlots.INPUTS, id + " takes " + recipe.ingredients().size() + " ingredients");
        }
        helper.assertTrue(seen > 50, "only " + seen + " Brewed recipes");
        // Bottles, wart, carrot, redstone, gunpowder and dragon's breath: six, more than the input slots.
        helper.assertTrue(at(helper.getLevel().getServer(), BREWING + "lingering_potion/minecraft/long_night_vision/"
                + "minecraft.nether_wart/minecraft.golden_carrot/minecraft.redstone/minecraft.gunpowder/minecraft.dragon_breath") == null,
                "a chain of six ingredients has a recipe");
        helper.succeed();
    }

    /** {@link TestBrews}: a BrewingRecipe from a water bottle, which ends in an item, and one from awkward, which makes a potion. */
    private static void modBrews(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AssemblingRecipe crystals = at(server, TestBrews.FROM_WATER);
        helper.assertTrue(crystals != null, TestBrews.FROM_WATER + " is not in the recipe map");
        helper.assertTrue(takesOnly(crystals, Items.AMETHYST_SHARD), "it takes " + crystals.ingredients());
        helper.assertTrue(crystals.results().size() == 1 && crystals.results().getFirst().item().value() == Items.PRISMARINE_CRYSTALS
                && crystals.results().getFirst().count() == 3, "it makes " + crystals.results());
        helper.assertTrue(crystals.time() == 400 && crystals.category() == AssemblingCategory.BREWING, "it is " + describe(crystals));
        check(helper, TestBrews.FROM_AWKWARD, 800, Items.POTION, Potions.LUCK, Items.NETHER_WART, Items.GLOW_BERRIES);
        check(helper, TestBrews.FROM_AWKWARD.replace("/potion/", "/splash_potion/") + "/minecraft.gunpowder", 1200, Items.SPLASH_POTION,
                Potions.LUCK, Items.NETHER_WART, Items.GLOW_BERRIES, Items.GUNPOWDER);
        helper.succeed();
    }

    private static void unlisted(GameTestHelper helper) {
        for (var holder : helper.getLevel().getServer().getRecipeManager().recipeMap().values()) {
            String id = holder.id().identifier().toString();
            helper.assertTrue(!id.startsWith(BREWING) || !id.contains("bone_meal"), id + " came from a brew that cannot be listed");
        }
        helper.succeed();
    }

    /**
     * Excludes one id, then switches brewing off, reading the recipes after each reload, and puts the config back
     * before passing or failing, since the rest of the run expects the defaults.
     */
    private static void excludesAndOff(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean();
        List<? extends String> excludedBefore = CraftworksConfig.MOD_RECIPES_EXCLUDED.get();
        boolean onBefore = CraftworksConfig.BREWING_RECIPES.get();
        String slowness = SLOWNESS + "minecraft.nether_wart/minecraft.sugar/minecraft.fermented_spider_eye";
        CraftworksConfig.MOD_RECIPES_EXCLUDED.set(List.of(NIGHT_VISION));
        ModRecipes.follow(server)
                .thenRun(() -> {
                    if (at(server, NIGHT_VISION) != null || at(server, slowness) == null) {
                        failure.compareAndSet(null, "excluding " + NIGHT_VISION + " should drop it and keep the rest; it is "
                                + describe(at(server, NIGHT_VISION)) + " and slowness is " + describe(at(server, slowness)));
                    }
                    CraftworksConfig.MOD_RECIPES_EXCLUDED.set(excludedBefore);
                    CraftworksConfig.BREWING_RECIPES.set(false);
                })
                .thenCompose(ignored -> ModRecipes.follow(server))
                .thenRun(() -> {
                    long left = server.getRecipeManager().recipeMap().values().stream()
                            .filter(holder -> holder.id().identifier().getPath().startsWith("brewing/")).count();
                    if (left != 0) failure.compareAndSet(null, "with brewingRecipes off, " + left + " Brewed recipes are left");
                })
                .handle((ignored, thrown) -> {
                    if (thrown != null) failure.compareAndSet(null, "the reload failed: " + thrown);
                    CraftworksConfig.MOD_RECIPES_EXCLUDED.set(excludedBefore);
                    CraftworksConfig.BREWING_RECIPES.set(onBefore);
                    return null;
                })
                .thenCompose(ignored -> ModRecipes.follow(server))
                .thenRun(() -> {
                    if (at(server, NIGHT_VISION) == null) failure.compareAndSet(null, "switched back on, " + NIGHT_VISION + " did not return");
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
}
