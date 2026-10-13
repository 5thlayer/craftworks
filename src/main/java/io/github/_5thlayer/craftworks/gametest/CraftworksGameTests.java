// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.api.LockHooks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Mod's game tests, run by the {@code gameTestServer} Gradle run: the Minecraft-integration
 * seam, with a real player on a real server. Each test stands on the {@code gametest/platform}
 * structure, a stone floor that {@code sh ./gradlew buildGametestStructures} writes, and sets up
 * what it needs itself, so the setup is in the diff.
 */
public final class CraftworksGameTests {

    /** Set by the Mod's own {@code gameTestServer} run, which registers the tests' own block and fluids. */
    private static final String OWN_RUN = "craftworks.gametest";

    /** Set by a {@code -PwithKubeJS} run, which puts KubeJS and the sample script on the server. */
    private static final String KUBEJS_TESTS = "craftworks.kubejsTests";

    private static final Identifier PLATFORM = id("gametest/platform");

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Craftworks.MOD_ID);

    static {
        TEST_TYPES.register("code", () -> CodeGameTest.CODEC);
    }

    private CraftworksGameTests() {
    }

    public static void register(IEventBus modBus) {
        TEST_TYPES.register(modBus);
        // Posted only when game tests are enabled, so a production server never registers the tests.
        modBus.addListener(CraftworksGameTests::registerTests);
        if (Boolean.getBoolean(OWN_RUN)) {
            // The tests' own fluid-handler block, which a player's world never has.
            TestTank.register(modBus);
            // And the fluids a five-fluid recipe needs beyond water and lava.
            TestFluids.register(modBus);
        }
        registerDevLockReason();
    }

    /**
     * Locks the one recipe {@code -Dcraftworks.devLockReason} names, with a reason, so a dev client can see
     * the Crafting Plan show one without Researchd (#18). Only the client run passes it, and only when asked.
     */
    private static void registerDevLockReason() {
        String recipe = System.getProperty("craftworks.devLockReason");
        if (recipe == null || recipe.isBlank()) return;
        Identifier locked = Identifier.parse(recipe);
        LockHooks.registerReasoned((player, asked) -> asked.equals(locked)
                ? LockHooks.Lock.because(Component.literal("Research: Dev Test"))
                : Optional.empty());
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        // Registered rather than borrowed, since the event hands out no lookup for vanilla's.
        var environment = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        var tests = new Registrar(event, environment);
        boolean kubeJS = Boolean.getBoolean(KUBEJS_TESTS);
        LoadTests.register(tests);
        PlaysAloneTests.register(tests, kubeJS);
        AssemblingRecipeTests.register(tests);
        AssemblerTests.register(tests);
        AssemblerMachineTests.register(tests);
        AssemblingCategoryTests.register(tests);
        AssemblerFluidTests.register(tests);
        AssemblerFluidSideTests.register(tests);
        AssemblerContainerTests.register(tests);
        CreativeEnergySourceTests.register(tests);
        RefinerTests.register(tests);
        CreativeFluidSourceTests.register(tests);
        CreativeTabTests.register(tests);
        AssemblerStateTests.register(tests);
        AssemblerGhostTests.register(tests);
        InventoryScreenTests.register(tests);
        LockTests.register(tests);
        ReadyTests.register(tests);
        CraftingPlanTests.register(tests);
        VanillaPackTests.register(tests);
        ModRecipesTests.register(tests);
        DataCarryingTests.register(tests);
        // KubeJS sends the mock player a payload of its own at login, which throws before the Mod's are checked (#51).
        if (!kubeJS) NoChannelTests.register(tests);
        if (kubeJS) KubeJSTests.register(tests);
        // Its own environment, so its own batch: it reloads the server's recipes, which no test running
        // beside it could stand.
        var reloading = event.registerEnvironment(id("reloads_recipes"), new TestEnvironmentDefinition.AllOf(List.of()));
        var reloadingTests = new Registrar(event, reloading);
        VanillaPackTests.registerReloading(reloadingTests);
        ModRecipesTests.registerReloading(reloadingTests);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, path);
    }

    /** What a test class is handed: a name, a tick budget and a body per test. */
    record Registrar(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {

        void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
            var id = id(name);
            CodeGameTest.define(id, body);
            event.registerTest(id, new CodeGameTest(id, new TestData<>(environment, PLATFORM, maxTicks, 0, true, Rotation.NONE)));
        }
    }
}
