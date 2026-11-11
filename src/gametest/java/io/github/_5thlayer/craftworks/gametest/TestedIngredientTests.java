// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.github._5thlayer.craftworks.assembler.PlayerPlanning;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * An ingredient that decides for itself which stacks it takes, {@code craftworks:gametest/tested_ingredient} (a milk
 * or water bucket, through a compound ingredient), is paid for by a stack carrying components it accepts: a named milk
 * bucket, as Neo Vitae's {@code orb_tier} is by a bound orb.
 */
final class TestedIngredientTests {

    private static final Identifier TESTED = Identifier.parse("craftworks:gametest/tested_ingredient");

    private TestedIngredientTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("a_stack_a_tested_ingredient_accepts_completes_the_plan", 20, TestedIngredientTests::plans);
        tests.test("a_stack_a_tested_ingredient_accepts_is_spent", 20, TestedIngredientTests::spent);
    }

    private static void plans(GameTestHelper helper) {
        ServerPlayer player = AssemblerTests.playerHolding(helper, named());
        helper.assertTrue(PlayerPlanning.of(player).view(TESTED).display().complete(),
                "a named milk bucket should pay for a milk or water bucket");
        helper.succeed();
    }

    private static void spent(GameTestHelper helper) {
        ServerPlayer player = AssemblerTests.playerHolding(helper, named());
        helper.assertTrue(PersonalAssembler.craft(player, TESTED, 1), "the craft should queue");
        helper.assertTrue(AssemblerTests.count(player, Items.MILK_BUCKET) == 0, "the named milk bucket was not taken");
        helper.succeed();
    }

    private static ItemStack named() {
        ItemStack bucket = new ItemStack(Items.MILK_BUCKET);
        bucket.set(DataComponents.CUSTOM_NAME, Component.literal("Daisy"));
        return bucket;
    }
}
