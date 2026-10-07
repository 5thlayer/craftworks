// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;

import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Craftworks' creative tab (#29): once built, it lists the six Craftworks items in order, and vanilla's
 * Functional Blocks holds none of them, so each shows in exactly one tab.
 */
final class CreativeTabTests {

    private CreativeTabTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("the_craftworks_tab_lists_its_items_in_order_and_functional_blocks_none", 20,
                CreativeTabTests::listsTheItems);
    }

    private static void listsTheItems(GameTestHelper helper) {
        var parameters = new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        CreativeModeTab craftworks = Assemblers.CREATIVE_TAB.get();
        CreativeModeTab functional = BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(CreativeModeTabs.FUNCTIONAL_BLOCKS);
        craftworks.buildContents(parameters);
        functional.buildContents(parameters);

        List<Item> expected = List.of(
                Assemblers.item(AssemblerTier.ONE).get(),
                Assemblers.item(AssemblerTier.TWO).get(),
                Assemblers.item(AssemblerTier.THREE).get(),
                Assemblers.CREATIVE_ENERGY_SOURCE_ITEM.get(),
                Assemblers.CREATIVE_FLUID_SOURCE_ITEM.get());
        List<Item> listed = craftworks.getDisplayItems().stream().map(ItemStack::getItem).toList();
        helper.assertTrue(listed.equals(expected), "the Craftworks tab listed " + listed + ", not " + expected);
        for (Item item : expected) {
            helper.assertFalse(functional.getDisplayItems().stream().anyMatch(stack -> stack.is(item)),
                    "Functional Blocks still holds " + item);
        }
        helper.assertTrue(craftworks.getIconItem().is(expected.getFirst()), "the Craftworks tab's icon is not the Assembler 1");
        helper.succeed();
    }
}
