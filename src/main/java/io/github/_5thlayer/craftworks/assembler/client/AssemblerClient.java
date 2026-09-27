// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler.client;

import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The client half of the Personal Assembler: the queue beside the hotbar.
 *
 * <p>Called only from {@code CraftworksClient}, so nothing here is loaded on a dedicated server.
 */
public final class AssemblerClient {

    private AssemblerClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AssemblerClient::registerHud);
    }

    /** Above the hotbar in draw order, so the queue is not painted under it. */
    private static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "assembler_queue"),
                new AssemblerHud());
    }
}
