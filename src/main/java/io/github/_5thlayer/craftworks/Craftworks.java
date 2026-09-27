// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks;

import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.github._5thlayer.craftworks.gametest.CraftworksGameTests;
import io.github._5thlayer.craftworks.network.CraftworksNetwork;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * The Mod's common half. Its feature is the Personal Assembler, the player's own crafting planner
 * (CONTEXT.md).
 */
@Mod(Craftworks.MOD_ID)
public final class Craftworks {

    public static final String MOD_ID = "craftworks";

    public Craftworks(IEventBus modBus) {
        CraftworksRecipes.register(modBus);
        CraftworksNetwork.register(modBus);
        PersonalAssembler.register(modBus);
        CraftworksGameTests.register(modBus);
    }
}
