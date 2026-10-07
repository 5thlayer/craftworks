// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Three fluids for the game tests, so a recipe can name five distinct ones with water and lava: a plain still fluid and
 * its flowing twin each, with no block or bucket, since only a tank holds them. Registered only when the game tests
 * run ({@code craftworks.gametestPack}), like {@link TestTank}, so no player's world has them.
 */
final class TestFluids {

    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Craftworks.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Craftworks.MOD_ID);

    static final DeferredHolder<Fluid, Fluid> ACID = new Registered("gametest_acid").still;
    static final DeferredHolder<Fluid, Fluid> BRINE = new Registered("gametest_brine").still;
    static final DeferredHolder<Fluid, Fluid> CRUDE = new Registered("gametest_crude").still;

    private TestFluids() {
    }

    /** One fluid: its type, its still fluid {@code name} and its flowing twin {@code name_flowing}. */
    private static final class Registered {

        final DeferredHolder<FluidType, FluidType> type;
        final DeferredHolder<Fluid, Fluid> still;
        final DeferredHolder<Fluid, Fluid> flowing;

        Registered(String name) {
            type = TYPES.register(name, () -> new FluidType(FluidType.Properties.create()));
            still = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(properties()));
            flowing = FLUIDS.register(name + "_flowing", () -> new BaseFlowingFluid.Flowing(properties()));
        }

        private BaseFlowingFluid.Properties properties() {
            return new BaseFlowingFluid.Properties(type, still, flowing);
        }
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
    }
}
