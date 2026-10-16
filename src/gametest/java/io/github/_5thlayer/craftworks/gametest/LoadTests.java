// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;

/**
 * The Mod loads on a server with nothing else installed, at the version its build gave it. It is the
 * harness's own check too: a run that reports this test ran the Mod's tests, not vanilla's.
 */
final class LoadTests {

    private LoadTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("craftworks_loads_on_a_server", 1, LoadTests::loads);
    }

    private static void loads(GameTestHelper helper) {
        var container = ModList.get().getModContainerById(Craftworks.MOD_ID);
        if (container.isEmpty()) {
            helper.fail("the server has not loaded " + Craftworks.MOD_ID);
            return;
        }
        // processResources writes mod_version into neoforge.mods.toml; unexpanded, the Pack's pin
        // and the loaded Mod would disagree on what is running.
        String version = container.get().getModInfo().getVersion().toString();
        if (version.contains("$")) {
            helper.fail("neoforge.mods.toml's version was not expanded: " + version);
            return;
        }
        helper.succeed();
    }
}
