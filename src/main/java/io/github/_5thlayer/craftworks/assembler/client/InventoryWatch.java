// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler.client;

import io.github._5thlayer.craftworks.network.InventoryWatchPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Tells the server when the inventory screen is open, because that is when it works out which recipes are
 * Ready and the only time (ADR-0012).
 *
 * <p>Polled each client tick rather than hooked to the screen's open and close: a screen can be replaced,
 * closed by the server or dropped with the world, and the poll sees all of them as "no longer the
 * inventory screen". Only a change is sent. The server forgets a player who disconnects, so leaving a
 * world needs no message, only {@link #reset()}.
 *
 * <p>Nothing is sent on a server that did not negotiate the payload, one without Craftworks.
 */
final class InventoryWatch {

    private static boolean reported;

    private InventoryWatch() {
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean open = minecraft.player != null && minecraft.screen instanceof InventoryScreen;
        if (open == reported) return;
        ClientPacketListener connection = minecraft.getConnection();
        InventoryWatchPacket packet = new InventoryWatchPacket(open);
        if (connection == null || !connection.hasChannel(packet)) return;
        ClientPacketDistributor.sendToServer(packet);
        reported = open;
    }

    /** A new connection starts with the server thinking the screen is closed. */
    static void reset() {
        reported = false;
    }
}
