// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;

import io.github._5thlayer.craftworks.recipe.AssemblingCategory;

/**
 * The figures a machine that crafts starts from before the server config says otherwise: an Assembler's
 * tier, or the Chemical Plant. The config has a section for each, named for its block. Pure: no Minecraft
 * types.
 */
public interface MachineDefaults {

    /** The block's name, which names its section of the server config. */
    String blockName();

    /** A craft takes the recipe's time divided by this, in ticks. */
    double defaultSpeed();

    /** FE a tick while crafting. */
    double defaultPower();

    /** FE the energy buffer holds. */
    int defaultBuffer();

    /** The recipe categories the machine holds. */
    List<AssemblingCategory> defaultCategories();
}
