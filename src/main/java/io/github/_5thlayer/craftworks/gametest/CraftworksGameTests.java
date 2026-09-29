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
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Mod's game tests, run by the {@code gameTestServer} Gradle run: the Minecraft-integration
 * seam, with a real player on a real server. Each test stands on the {@code gametest/platform}
 * structure, a stone floor that {@code scripts/build-gametest-structures.py} writes, and sets up
 * what it needs itself, so the setup is in the diff.
 */
public final class CraftworksGameTests {

    private static final String DEV_PACK = "craftworks.devPack";
    private static final String GAMETEST_PACK = "craftworks.gametestPack";

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
        if (Boolean.getBoolean(DEV_PACK)) modBus.addListener(CraftworksGameTests::addDevPack);
        if (Boolean.getBoolean(GAMETEST_PACK)) modBus.addListener(CraftworksGameTests::addGameTestPack);
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

    /**
     * A dev world's Assembling recipes, in {@code dev_pack/} in the jar: vanilla's flint and steel at its
     * own id, until the built-in vanilla pack exists, and two stick routes at different Route priorities
     * (planks over bamboo) to show the fallback. Only the dev runs set {@value #DEV_PACK}, so a player's
     * world never sees it.
     */
    private static void addDevPack(AddPackFindersEvent event) {
        event.addPackFinders(id("dev_pack"), PackType.SERVER_DATA, Component.literal("Craftworks dev recipes"),
                PackSource.BUILT_IN, true, Pack.Position.TOP);
    }

    /**
     * The recipes only the game tests read, in {@code gametest_pack/} in the jar. Only the
     * {@code gameTestServer} run sets {@value #GAMETEST_PACK}, so no dev or player world sees them.
     */
    private static void addGameTestPack(AddPackFindersEvent event) {
        event.addPackFinders(id("gametest_pack"), PackType.SERVER_DATA, Component.literal("Craftworks game tests"),
                PackSource.BUILT_IN, true, Pack.Position.TOP);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        // Registered rather than borrowed, since the event hands out no lookup for vanilla's.
        var environment = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        var tests = new Registrar(event, environment);
        LoadTests.register(tests);
        AssemblingRecipeTests.register(tests);
        AssemblerTests.register(tests);
        InventoryScreenTests.register(tests);
        LockTests.register(tests);
        CraftingPlanTests.register(tests);
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
