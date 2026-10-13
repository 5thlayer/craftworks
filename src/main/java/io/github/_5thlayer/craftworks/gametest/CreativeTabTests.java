// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.libworks.testkit.WorksTabCheck;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Every Craftworks item shows in the suite's shared Works tab, in order, and in no other tab, and Craftworks
 * keeps no tab of its own (#48).
 */
final class CreativeTabTests {

    private static final List<String> WORKS_TAB_ORDER = List.of(
            "assembler_1", "assembler_2", "assembler_3", "refiner", "creative_energy_source", "creative_fluid_source");

    private CreativeTabTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("the_works_tab_lists_every_craftworks_item_in_order", 1,
                helper -> WorksTabCheck.inOrder(helper, Craftworks.MOD_ID, WORKS_TAB_ORDER));
        tests.test("no_other_tab_holds_a_craftworks_item", 1,
                helper -> WorksTabCheck.noOtherTab(helper, Craftworks.MOD_ID));
        tests.test("craftworks_registers_no_creative_tab", 1, CreativeTabTests::noOwnTab);
    }

    private static void noOwnTab(GameTestHelper helper) {
        var own = BuiltInRegistries.CREATIVE_MODE_TAB.keySet().stream()
                .filter(id -> id.getNamespace().equals(Craftworks.MOD_ID))
                .toList();
        if (!own.isEmpty()) {
            helper.fail("Craftworks registers its own creative tab: " + own);
            return;
        }
        helper.succeed();
    }
}
