// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * An open machine's screen as the Held recipe reaches it: Fill Recipe on the server, and the recipe the
 * server sends back for the client to name and ghost its slots from. The two packets of that trip hold an
 * Assembler's menu and a Chemical Plant's alike.
 */
public interface HeldRecipeMenu {

    /**
     * Holds {@code id} if the player may set it, or tells them why not. The Lock source is asked here, of this
     * player, and never again (ADR-0013).
     */
    HoldVerdict request(ServerPlayer player, Identifier id);

    /** The client taking the Held recipe the server sent. */
    void show(Optional<AssemblerMenu.Held> held);
}
