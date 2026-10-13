// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;

import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.HeldRecipes;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A mod's recipe that assembles its own result, {@code craftworks:gametest/keeps_data} (a diamond over an iron sword
 * makes a diamond sword with the sword's name and damage), is a Data-carrying recipe (ADR-0022): converted, and the
 * Assembler machine asks the original what each craft made.
 */
final class SelfAssemblingTests {

    static final Identifier KEEPS_DATA = Identifier.parse("craftworks:gametest/keeps_data");
    /** Shapeless and plain: two sand and a dirt make three clay balls. */
    private static final Identifier PLAIN = Identifier.parse("craftworks:gametest/converted_shapeless");

    private SelfAssemblingTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("a_self_assembling_recipe_converts_at_its_id", 20, SelfAssemblingTests::converts);
        tests.test("the_machine_makes_what_a_self_assembling_recipe_assembles", 40, SelfAssemblingTests::machineKeeps);
        tests.test("the_machine_makes_the_listed_product_of_a_recipe_that_assembles_nothing", 40, SelfAssemblingTests::machinePlain);
    }

    private static void converts(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RecipeType<?> type = ModRecipesTests.type(server, KEEPS_DATA.toString());
        helper.assertTrue(type == CraftworksRecipes.ASSEMBLING_TYPE.get(), KEEPS_DATA + " should be a Converted recipe, and is " + type);
        helper.succeed();
    }

    /** The machine takes the named, damaged sword as it is, and the original recipe copies both. */
    private static void machineKeeps(GameTestHelper helper) {
        ItemStack made = machineCraft(helper, KEEPS_DATA, named());
        helper.assertTrue(made.is(Items.DIAMOND_SWORD) && Component.literal("Biter").equals(made.get(DataComponents.CUSTOM_NAME))
                        && made.getDamageValue() == 42,
                "the machine should make a diamond sword named Biter at damage 42, and made " + made + " named "
                        + made.get(DataComponents.CUSTOM_NAME) + " at damage " + made.getDamageValue());
        helper.succeed();
    }

    private static void machinePlain(GameTestHelper helper) {
        ItemStack made = machineCraft(helper, PLAIN, ItemStack.EMPTY);
        helper.assertTrue(made.is(Items.CLAY_BALL), "the machine should make clay balls, and made " + made);
        helper.succeed();
    }

    static ItemStack named() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Biter"));
        sword.setDamageValue(42);
        return sword;
    }

    /**
     * One craft on an Assembler 3: each slot is filled with {@code special} if its ingredient takes it, else
     * with an item its ingredient lists; what lands in the first output.
     */
    private static ItemStack machineCraft(GameTestHelper helper, Identifier recipe, ItemStack special) {
        var assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, recipe);
        var bag = HeldRecipes.find(helper.getLevel(), recipe).orElseThrow().value().ingredients();
        for (int slot = 0; slot < bag.size(); slot++) {
            var sized = bag.get(slot);
            ItemStack stack = !special.isEmpty() && sized.ingredient().test(special) ? special
                    : sized.ingredient().items().findFirst().map(h -> new ItemStack(h.value())).orElseThrow();
            assembler.machine().inventory().set(slot, ItemResource.of(stack), sized.count());
        }
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int tick = 0; tick < 20 && AssemblerMachineTests.count(assembler, AssemblerSlots.OUTPUT) == 0; tick++) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        return assembler.machine().inventory().getResource(AssemblerSlots.OUTPUT).toStack(1);
    }
}
