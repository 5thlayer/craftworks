// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;

import net.minecraft.resources.Identifier;

/**
 * What Jade reads of a machine that crafts its Held recipe: an Assembler or a Chemical Plant. Server only
 * where the Held recipe has to be resolved: anywhere else {@link #state} reads as
 * {@link AssemblerState#NO_RECIPE}.
 */
public interface HeldMachine {

    Optional<Identifier> heldRecipe();

    /** What the machine is doing, asked the way its tick asks and changing nothing. */
    AssemblerState state();

    /** Ticks into the craft under way. */
    int craftProgress();

    /** The Held recipe's ticks at the machine's speed, or 0 with none that runs. Server only. */
    int craftDuration();
}
