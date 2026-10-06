// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.network;

import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import io.github._5thlayer.craftworks.recipe.RuntimeAssemblingRecipes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The Mod's payloads, and when the server sends them. Every payload to a client goes through
 * {@link #sendToPlayer}, never {@code PacketDistributor} directly, so none reaches a connection without its channel.
 */
public final class CraftworksNetwork {

    /** Bumped when a payload's shape changes; clients on the old shape are refused, not confused. */
    private static final String VERSION = "6";

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
        registrar.playToClient(ReadyRecipesPacket.TYPE, ReadyRecipesPacket.STREAM_CODEC, ReadyRecipesPacket::handle);
        registrar.playToServer(InventoryWatchPacket.TYPE, InventoryWatchPacket.STREAM_CODEC, InventoryWatchPacket::handle);
        registrar.playToClient(QueueSyncPacket.TYPE, QueueSyncPacket.STREAM_CODEC, QueueSyncPacket::handle);
        registrar.playToClient(AssemblerHeldPacket.TYPE, AssemblerHeldPacket.STREAM_CODEC, AssemblerHeldPacket::handle);
        registrar.playToServer(HoldRecipePacket.TYPE, HoldRecipePacket.STREAM_CODEC, HoldRecipePacket::handle);
        registrar.playToServer(FillRecipePacket.TYPE, FillRecipePacket.STREAM_CODEC, FillRecipePacket::handle);
        registrar.playToServer(PlanCancelPacket.TYPE, PlanCancelPacket.STREAM_CODEC, PlanCancelPacket::handle);
        registrar.playToServer(PlanCraftPacket.TYPE, PlanCraftPacket.STREAM_CODEC, PlanCraftPacket::handle);
        registrar.playToClient(PlanUpdatePacket.TYPE, PlanUpdatePacket.STREAM_CODEC, PlanUpdatePacket::handle);
    }

    /**
     * The Assembling recipe set, sent on the two occasions it can change: datapack sync fires on login
     * with one player and on {@code /reload} with none.
     *
     * <p>The recipes themselves go too: a client is sent only the recipe types asked for, and a recipe
     * viewer on a dedicated server's client has nothing else to draw them from.
     */
    private static void onDatapackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(CraftworksRecipes.ASSEMBLING_TYPE.get());
        if (event.getPlayer() != null) {
            sendRecipeSet(event.getPlayer());
        } else {
            event.getPlayerList().getPlayers().forEach(CraftworksNetwork::sendRecipeSet);
        }
    }

    private static void sendRecipeSet(ServerPlayer player) {
        sendToPlayer(player, AssemblingRecipeSetPacket.of(RuntimeAssemblingRecipes.recipes(player.level())));
    }

    /**
     * Sends the payload if the player's connection opened its channel, and drops it if not.
     *
     * <p>NeoForge throws on a payload down a channel the client never opened, and these are sent from
     * event listeners, where a throw takes down whatever fired the event: a game test's mock player
     * negotiates no channels and logs in all the same. A player without the channel has no Craftworks
     * to show the payload in, so it is dropped rather than sent.
     */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
