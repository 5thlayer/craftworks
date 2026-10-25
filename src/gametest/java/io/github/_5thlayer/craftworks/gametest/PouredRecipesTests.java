// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.gametest.AssemblerMachineTests.Placed;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.FluidLayout;
import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import io.github._5thlayer.craftworks.recipe.ModRecipes;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Poured recipes (#80, ADR-0027): a Converted recipe that takes or makes a filled bucket also gives an Assembling
 * recipe at {@code craftworks:fluid/<namespace>/<path>} that takes and makes the fluid instead. The sources are the
 * {@code poured_recipes} pack's crafting recipes; the exclude list is tried on the {@code poured_excluded} pack's, in
 * a batch of its own since it reloads the recipes.
 */
final class PouredRecipesTests {

    private static final String PACK = "poured_recipes";
    private static final String EXCLUDED_PACK = "poured_excluded";

    private static final String LAVA_TO_WATER = "craftworks:poured_lava_to_water";
    private static final String BUCKET_SWAP = "craftworks:poured_bucket_swap";
    private static final String THREE_LAVA = "craftworks:poured_three_lava";
    private static final String LAVA_AND_WATER = "craftworks:poured_lava_and_water";
    private static final String KEEPS_DATA = "craftworks:poured_keeps_data";
    private static final String WRAPS = "craftworks:poured_wraps";
    private static final String TAKEN = "craftworks:poured_taken";
    private static final String MIXED = "craftworks:poured_mixed";
    private static final String POURED_EXCLUDED = "craftworks:poured_poured_excluded";
    private static final String SOURCE_EXCLUDED = "craftworks:poured_source_excluded";

    private PouredRecipesTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        var packed = tests.withPack(PACK);
        packed.test("poured_a_bucket_craft_gives_a_fluid_recipe_beside_it", 20, PouredRecipesTests::pours);
        packed.test("poured_an_assembler_makes_water_from_lava_in_its_box_and_gives_back_no_bucket", 100, PouredRecipesTests::assembles);
        packed.test("poured_an_empty_bucket_ingredient_is_dropped_for_a_filled_bucket_made", 20, PouredRecipesTests::dropsTheEmptyBucket);
        packed.test("poured_two_filled_buckets_in_make_two_fluids_in_and_a_plain_item_out", 20, PouredRecipesTests::twoFluidsIn);
        packed.test("poured_three_buckets_of_one_fluid_are_more_than_a_box_and_get_none", 20, PouredRecipesTests::tooMuch);
        packed.test("poured_an_ingredient_mixing_a_bucket_with_a_plain_item_gets_none", 20, PouredRecipesTests::mixed);
        packed.test("poured_a_recipe_with_no_filled_bucket_gets_none", 20, PouredRecipesTests::noBucket);
        packed.test("poured_a_data_carrying_recipe_gets_none", 20, PouredRecipesTests::dataCarrying);
        packed.test("poured_a_wrapped_recipe_is_data_carrying_and_gets_none", 20, PouredRecipesTests::wrapped);
        packed.test("poured_an_id_already_taken_is_left_alone", 20, PouredRecipesTests::taken);
    }

    static void registerReloading(CraftworksGameTests.Registrar tests) {
        tests.withPack(EXCLUDED_PACK).test("poured_an_excluded_poured_id_gets_no_recipe_and_an_excluded_source_gets_neither", 600,
                PouredRecipesTests::excludes);
    }

    private static Identifier pouredId(String source) {
        Identifier id = Identifier.parse(source);
        return Identifier.fromNamespaceAndPath("craftworks", "fluid/" + id.getNamespace() + "/" + id.getPath());
    }

    private static AssemblingRecipe at(MinecraftServer server, String id) {
        RecipeHolder<?> holder = server.getRecipeManager().recipeMap().byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)));
        return holder != null && holder.value() instanceof AssemblingRecipe recipe ? recipe : null;
    }

    private static AssemblingRecipe poured(MinecraftServer server, String source) {
        return at(server, pouredId(source).toString());
    }

    private static void pours(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AssemblingRecipe source = at(server, LAVA_TO_WATER);
        helper.assertTrue(source != null, LAVA_TO_WATER + " should still be a Converted recipe");
        AssemblingRecipe pour = poured(server, LAVA_TO_WATER);
        helper.assertTrue(pour != null, "a lava bucket and dirt making a water bucket should pour at " + pouredId(LAVA_TO_WATER));
        helper.assertTrue(pour.fluidIngredients().size() == 1 && pour.fluidIngredients().getFirst().amount() == 1000
                && pour.fluidIngredients().getFirst().test(new FluidStack(Fluids.LAVA, 1000))
                && !pour.fluidIngredients().getFirst().test(new FluidStack(Fluids.WATER, 1000)),
                "it should take 1000 mB of lava, and takes " + pour.fluidIngredients());
        helper.assertTrue(pour.ingredients().size() == 1 && pour.ingredients().getFirst().count() == 1
                && pour.ingredients().getFirst().ingredient().test(new ItemStack(Items.DIRT)),
                "it should take the dirt alone, and takes " + pour.ingredients());
        helper.assertTrue(pour.fluidResults().size() == 1 && pour.fluidResults().getFirst().fluid().value() == Fluids.WATER
                && pour.fluidResults().getFirst().amount() == 1000, "it should make 1000 mB of water, and makes " + pour.fluidResults());
        helper.assertTrue(pour.results().isEmpty(), "it should make no item, and makes " + pour.results());
        helper.assertTrue(pour.category() == AssemblingCategory.CRAFTING_WITH_FLUID, "its category is " + pour.category());
        helper.assertTrue(pour.time() == source.time() && pour.priority() == source.priority(),
                "it should keep its source's time " + source.time() + " and priority " + source.priority() + ", and has "
                        + pour.time() + " and " + pour.priority());
        helper.assertTrue(!pour.handCraftable(), "a Poured recipe has a fluid and is never Hand-craftable");
        helper.assertTrue(source.fluidIngredients().isEmpty()
                && source.results().size() == 1 && source.results().getFirst().is(Items.WATER_BUCKET),
                "the Converted recipe should be as it was, and is " + source);
        helper.succeed();
    }

    /** Lava already in the input box and a dirt in the slot: the craft puts water in an output box, and no bucket anywhere. */
    private static void assembles(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMachineTests.hold(assembler, pouredId(LAVA_TO_WATER));
        assembler.machine().fluids().set(0, new FluidStack(Fluids.LAVA, 2000));
        helper.assertTrue(AssemblerMachineTests.insert(assembler, 0, Items.DIRT, 1) == 1, "the dirt did not go in");
        int waterBox = FluidLayout.ASSEMBLER.fluidInputs();
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        int ran = 0;
        while (assembler.machine().fluids().contents(waterBox).isEmpty() && ran < 100) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
            ran++;
        }
        FluidStack made = assembler.machine().fluids().contents(waterBox);
        helper.assertTrue(made.getFluid() == Fluids.WATER && made.getAmount() == 1000, "the output box holds " + made + " after " + ran + " ticks");
        helper.assertTrue(assembler.machine().fluids().contents(0).getFluid() == Fluids.LAVA
                && assembler.machine().fluids().contents(0).getAmount() == 1000, "the input box should hold the 1000 mB of lava left, and holds "
                + assembler.machine().fluids().contents(0));
        helper.assertTrue(AssemblerMachineTests.count(assembler, 0) == 0, "the dirt was not used");
        for (int n = 0; n < AssemblerSlots.OUTPUTS; n++) {
            helper.assertTrue(AssemblerMachineTests.count(assembler, AssemblerSlots.output(n)) == 0,
                    "output " + n + " holds an item, but a Poured recipe gives back no bucket");
        }
        helper.succeed();
    }

    private static void dropsTheEmptyBucket(GameTestHelper helper) {
        AssemblingRecipe pour = poured(helper.getLevel().getServer(), BUCKET_SWAP);
        helper.assertTrue(pour != null, "a water bucket, an empty bucket and soul sand making a lava bucket should pour");
        helper.assertTrue(pour.ingredients().size() == 1 && pour.ingredients().getFirst().ingredient().test(new ItemStack(Items.SOUL_SAND))
                && !pour.ingredients().getFirst().ingredient().test(new ItemStack(Items.BUCKET)),
                "it should take the soul sand alone, and takes " + pour.ingredients());
        helper.assertTrue(pour.fluidIngredients().size() == 1 && pour.fluidIngredients().getFirst().test(new FluidStack(Fluids.WATER, 1000))
                && pour.fluidIngredients().getFirst().amount() == 1000, "it should take 1000 mB of water, and takes " + pour.fluidIngredients());
        helper.assertTrue(pour.fluidResults().size() == 1 && pour.fluidResults().getFirst().fluid().value() == Fluids.LAVA
                && pour.fluidResults().getFirst().amount() == 1000 && pour.results().isEmpty(),
                "it should make 1000 mB of lava and no item, and makes " + pour.fluidResults() + " and " + pour.results());
        helper.succeed();
    }

    private static void twoFluidsIn(GameTestHelper helper) {
        AssemblingRecipe pour = poured(helper.getLevel().getServer(), LAVA_AND_WATER);
        helper.assertTrue(pour != null, "a lava bucket and a water bucket making obsidian should pour");
        helper.assertTrue(pour.ingredients().isEmpty() && pour.fluidIngredients().size() == 2 && pour.fluidResults().isEmpty()
                && pour.results().size() == 1 && pour.results().getFirst().is(Items.OBSIDIAN)
                && pour.fluidIngredients().stream().allMatch(fluid -> fluid.amount() == 1000)
                && pour.fluidIngredients().get(0).test(new FluidStack(Fluids.LAVA, 1000))
                && pour.fluidIngredients().get(1).test(new FluidStack(Fluids.WATER, 1000)),
                "it should take 1000 mB of lava and of water to make obsidian, and is " + pour);
        helper.succeed();
    }

    private static void tooMuch(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(at(server, THREE_LAVA) != null, THREE_LAVA + " should still be a Converted recipe");
        helper.assertTrue(poured(server, THREE_LAVA) == null, "3,000 mB of lava is more than a box, and still got a Poured recipe");
        helper.succeed();
    }

    private static void mixed(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(at(server, MIXED) != null, MIXED + " should be a Converted recipe");
        helper.assertTrue(poured(server, MIXED) == null, "an ingredient of a lava bucket or a dirt still got a Poured recipe");
        helper.succeed();
    }

    private static void noBucket(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String id : List.of("craftworks:gametest/converted_shaped", "craftworks:gametest/converted_shapeless", "minecraft:crafting_table",
                "minecraft:iron_block")) {
            helper.assertTrue(at(server, id) != null, id + " should be a Converted recipe");
            helper.assertTrue(poured(server, id) == null, id + " has no filled bucket, and got a Poured recipe");
        }
        helper.succeed();
    }

    /** A lava bucket recipe whose original assembles its own result is Data-carrying and gets none. */
    private static void dataCarrying(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(at(server, KEEPS_DATA) != null, KEEPS_DATA + " should be a Converted recipe");
        helper.assertTrue(poured(server, KEEPS_DATA) == null, "a Data-carrying recipe got a Poured recipe");
        helper.succeed();
    }

    private static void wrapped(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(at(server, WRAPS) != null, WRAPS + " should be a Converted recipe");
        helper.assertTrue(poured(server, WRAPS) == null, "a wrapped recipe is Data-carrying and still got a Poured recipe");
        helper.succeed();
    }

    /** The pack has its own Assembling recipe at the Poured id of {@code poured_taken}: it stays as the pack wrote it. */
    private static void taken(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(at(server, TAKEN) != null, TAKEN + " should be a Converted recipe");
        AssemblingRecipe there = poured(server, TAKEN);
        helper.assertTrue(there != null && there.time() == 77 && there.fluidIngredients().isEmpty() && there.results().size() == 1
                && there.results().getFirst().is(Items.OBSIDIAN), "the pack's own recipe at " + pouredId(TAKEN) + " was replaced by " + there);
        helper.succeed();
    }

    /**
     * Excludes one Poured id and one source id, reading the recipes after the reload, then puts the config back
     * and checks both return, since the rest of the run expects the defaults.
     */
    private static void excludes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean();
        List<? extends String> excludedBefore = CraftworksConfig.MOD_RECIPES_EXCLUDED.get();
        if (at(server, POURED_EXCLUDED) == null || poured(server, POURED_EXCLUDED) == null || poured(server, SOURCE_EXCLUDED) == null) {
            helper.fail("before excluding, both sources should be Converted and pour");
            return;
        }
        CraftworksConfig.MOD_RECIPES_EXCLUDED.set(List.of(pouredId(POURED_EXCLUDED).toString(), SOURCE_EXCLUDED));
        ModRecipes.follow(server)
                .thenRun(() -> {
                    if (at(server, POURED_EXCLUDED) == null || poured(server, POURED_EXCLUDED) != null) {
                        failure.compareAndSet(null, "excluding " + pouredId(POURED_EXCLUDED) + " should drop the Poured recipe and keep the Converted one; "
                                + "the Converted recipe is " + at(server, POURED_EXCLUDED) + " and the Poured " + poured(server, POURED_EXCLUDED));
                    }
                    if (at(server, SOURCE_EXCLUDED) != null || poured(server, SOURCE_EXCLUDED) != null) {
                        failure.compareAndSet(null, "excluding " + SOURCE_EXCLUDED + " should leave it at the table and derive nothing; "
                                + "the Converted recipe is " + at(server, SOURCE_EXCLUDED) + " and the Poured " + poured(server, SOURCE_EXCLUDED));
                    }
                })
                .handle((ignored, thrown) -> {
                    if (thrown != null) failure.compareAndSet(null, "the reload failed: " + thrown);
                    CraftworksConfig.MOD_RECIPES_EXCLUDED.set(excludedBefore);
                    return null;
                })
                .thenCompose(ignored -> ModRecipes.follow(server))
                .thenRun(() -> {
                    if (poured(server, POURED_EXCLUDED) == null || poured(server, SOURCE_EXCLUDED) == null) {
                        failure.compareAndSet(null, "with nothing excluded again, both Poured recipes should return");
                    }
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
