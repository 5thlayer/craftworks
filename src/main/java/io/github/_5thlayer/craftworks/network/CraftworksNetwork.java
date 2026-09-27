// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.network;

import io.github._5thlayer.craftworks.recipe.RuntimeAssemblingRecipes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The Mod's payloads, and when the server sends them. */
public final class CraftworksNetwork {

    /** Bumped when a payload's shape changes; clients on the old shape are refused, not confused. */
    private static final String VERSION = "1";

    private CraftworksNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(CraftworksNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(CraftworksNetwork::onDatapackSync);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(VERSION);
        registrar.playToClient(AssemblingRecipeSetPacket.TYPE, AssemblingRecipeSetPacket.STREAM_CODEC,
                AssemblingRecipeSetPacket::handle);
    }

    /**
     * The Assembling recipe set, sent on the two occasions it can change: datapack sync fires on login
     * with one player and on {@code /reload} with none.
     */
    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            sendRecipeSet(event.getPlayer());
        } else {
            event.getPlayerList().getPlayers().forEach(CraftworksNetwork::sendRecipeSet);
        }
    }

    private static void sendRecipeSet(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                AssemblingRecipeSetPacket.of(RuntimeAssemblingRecipes.recipes(player.level()).ids()));
    }
}
