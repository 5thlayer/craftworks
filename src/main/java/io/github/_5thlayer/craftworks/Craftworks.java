// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks;

import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.github._5thlayer.craftworks.gametest.CraftworksGameTests;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.FluidMachines;
import io.github._5thlayer.craftworks.network.CraftworksNetwork;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import io.github._5thlayer.craftworks.recipe.VanillaPack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * The Mod's common half. Its feature is the Personal Assembler, the player's own crafting planner
 * (CONTEXT.md).
 */
@Mod(Craftworks.MOD_ID)
public final class Craftworks {

    public static final String MOD_ID = "craftworks";

    public Craftworks(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, CraftworksConfig.SPEC);
        CraftworksRecipes.register(modBus);
        VanillaPack.register(modBus);
        CraftworksNetwork.register(modBus);
        PersonalAssembler.register(modBus);
        Assemblers.register(modBus);
        FluidMachines.register(modBus);
        CraftworksGameTests.register(modBus);
    }
}
