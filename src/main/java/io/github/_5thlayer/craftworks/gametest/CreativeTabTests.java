// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.Refiners;
import io.github._5thlayer.libworks.WorksTab;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Every Craftworks item shows in the suite's shared Works tab, in order, and in no other tab, and Craftworks
 * keeps no tab of its own (#48).
 */
final class CreativeTabTests {

    private CreativeTabTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("the_works_tab_lists_every_craftworks_item_in_order_and_functional_blocks_none", 20,
                CreativeTabTests::listsTheItems);
        tests.test("craftworks_registers_no_creative_tab", 1, CreativeTabTests::noOwnTab);
    }

    private static void listsTheItems(GameTestHelper helper) {
        var parameters = new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        CreativeModeTab works = BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(WorksTab.KEY);
        CreativeModeTab functional = BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(CreativeModeTabs.FUNCTIONAL_BLOCKS);
        works.buildContents(parameters);
        functional.buildContents(parameters);

        List<Item> expected = List.of(
                Assemblers.item(AssemblerTier.ONE).get(),
                Assemblers.item(AssemblerTier.TWO).get(),
                Assemblers.item(AssemblerTier.THREE).get(),
                Refiners.ITEM.get(),
                Assemblers.CREATIVE_ENERGY_SOURCE_ITEM.get(),
                Assemblers.CREATIVE_FLUID_SOURCE_ITEM.get());
        List<Item> registered = BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Craftworks.MOD_ID))
                .toList();
        helper.assertTrue(registered.size() == expected.size() && registered.containsAll(expected),
                "Craftworks registers " + registered + ", not " + expected);
        List<Item> listed = works.getDisplayItems().stream().map(ItemStack::getItem)
                .filter(registered::contains).toList();
        helper.assertTrue(listed.equals(expected), "the Works tab listed " + listed + ", not " + expected);
        for (Item item : expected) {
            helper.assertFalse(functional.getDisplayItems().stream().anyMatch(stack -> stack.is(item)),
                    "Functional Blocks still holds " + item);
        }
        helper.succeed();
    }

    private static void noOwnTab(GameTestHelper helper) {
        var own = BuiltInRegistries.CREATIVE_MODE_TAB.keySet().stream()
                .filter(id -> id.getNamespace().equals(Craftworks.MOD_ID))
                .toList();
        helper.assertTrue(own.isEmpty(), "Craftworks registers its own creative tab: " + own);
        helper.succeed();
    }
}
