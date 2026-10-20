// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;

import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.HeldRecipes;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import io.github._5thlayer.craftworks.recipe.DataCarryingRecipes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A recipe that wraps a shaped one without extending it, {@code craftworks:gametest/wraps_shaped} (#68, ADR-0023),
 * converts through the recipe it wraps and is a Data-carrying recipe, though it says {@code isSpecial()}.
 */
final class WrappedRecipeTests {

    private static final Identifier WRAPS = Identifier.parse("craftworks:gametest/wraps_shaped");

    private WrappedRecipeTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("wrapped_recipe_converts_at_its_id", 20, WrappedRecipeTests::converts);
        tests.test("wrapped_recipe_is_paid_by_the_named_sword", 20, WrappedRecipeTests::namedSwordPays);
        tests.test("wrapped_recipe_delivers_a_named_diamond_sword", 200, WrappedRecipeTests::delivers);
        tests.test("wrapped_recipe_is_made_by_the_machine", 40, WrappedRecipeTests::machineMakes);
        tests.test("wrapped_recipe_is_asked_only_when_a_craft_finishes", 60, WrappedRecipeTests::askedOnlyOnFinish);
    }

    private static void converts(GameTestHelper helper) {
        RecipeType<?> type = ModRecipesTests.type(helper.getLevel().getServer(), WRAPS.toString());
        helper.assertTrue(type == CraftworksRecipes.ASSEMBLING_TYPE.get(), WRAPS + " should be a Converted recipe, and is " + type);
        helper.assertTrue(DataCarryingRecipes.isDataCarrying(WRAPS), WRAPS + " should be a Data-carrying recipe");
        helper.succeed();
    }

    private static void namedSwordPays(GameTestHelper helper) {
        ServerPlayer player = AssemblerTests.playerHolding(helper, DataCarryingTests.named());
        player.getInventory().add(new ItemStack(Items.DIAMOND));
        helper.assertTrue(PersonalAssembler.craft(player, WRAPS, 1), "a named sword and a diamond should queue " + WRAPS);
        helper.assertTrue(AssemblerTests.count(player, Items.IRON_SWORD) == 0 && AssemblerTests.count(player, Items.DIAMOND) == 0,
                "queueing should have taken the named sword and the diamond");
        helper.succeed();
    }

    private static void delivers(GameTestHelper helper) {
        ServerPlayer player = AssemblerTests.playerHolding(helper, DataCarryingTests.named());
        player.getInventory().add(new ItemStack(Items.DIAMOND));
        helper.assertTrue(PersonalAssembler.craft(player, WRAPS, 1), "the recipe should queue");
        helper.onEachTick(() -> EventHooks.firePlayerTickPost(player));
        helper.succeedWhen(() -> {
            helper.assertTrue(PersonalAssembler.queueOf(player).isEmpty(), "the plan is still queued");
            List<ItemStack> swords = player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(Items.DIAMOND_SWORD)).toList();
            helper.assertTrue(swords.size() == 1 && Component.literal("Biter").equals(swords.get(0).get(DataComponents.CUSTOM_NAME))
                            && swords.get(0).getDamageValue() == 42,
                    "one diamond sword named Biter at damage 42 should be delivered, and " + swords + " are");
        });
    }

    /** The wrapper only answers a grid laid out as the wrapped pattern, so this also checks the layout. */
    private static void machineMakes(GameTestHelper helper) {
        ItemStack made = machineCraft(helper, null);
        helper.assertTrue(made.is(Items.DIAMOND_SWORD) && Component.literal("Biter").equals(made.get(DataComponents.CUSTOM_NAME))
                        && made.getDamageValue() == 42,
                "the machine should make a diamond sword named Biter at damage 42, and made " + made);
        helper.succeed();
    }

    private static void askedOnlyOnFinish(GameTestHelper helper) {
        WrapsShapedRecipe.ASSEMBLES.set(0);
        int[] ticks = new int[1];
        ItemStack made = machineCraft(helper, ticks);
        helper.assertTrue(made.is(Items.DIAMOND_SWORD), "the machine should make a diamond sword, and made " + made);
        helper.assertTrue(ticks[0] > 1, "the craft should take several ticks, and took " + ticks[0]);
        helper.assertTrue(WrapsShapedRecipe.ASSEMBLES.get() == 1,
                "the recipe should be asked once, for the one finished craft, and was asked " + WrapsShapedRecipe.ASSEMBLES.get()
                        + " times over " + ticks[0] + " ticks");
        helper.succeed();
    }

    /** One craft of the named sword and a diamond on an Assembler 3; what lands in the first output. */
    private static ItemStack machineCraft(GameTestHelper helper, int[] ticks) {
        var assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, WRAPS);
        var bag = HeldRecipes.find(helper.getLevel(), WRAPS).orElseThrow().value().ingredients();
        for (int slot = 0; slot < bag.size(); slot++) {
            var sized = bag.get(slot);
            ItemStack named = DataCarryingTests.named();
            ItemStack stack = sized.ingredient().test(named) ? named : new ItemStack(Items.DIAMOND);
            assembler.machine().inventory().set(slot, ItemResource.of(stack), sized.count());
        }
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        int tick = 0;
        for (; tick < 20 && AssemblerMachineTests.count(assembler, AssemblerSlots.OUTPUT) == 0; tick++) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        if (ticks != null) ticks[0] = tick;
        return assembler.machine().inventory().getResource(AssemblerSlots.OUTPUT).toStack(1);
    }
}
