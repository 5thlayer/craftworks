// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The origin block of a fluid machine: it says which of its block state's properties is true while the machine's
 * Fluid Connections exist, which its model draws the rings from, so the block entity keeps whichever it names.
 */
public interface FluidMachineBlock {

    /** The property of the origin's block state that is true while the Fluid Connections exist. */
    BooleanProperty connectionsProperty();
}
