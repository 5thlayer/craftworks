// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.network;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.assembler.ReadyWatch;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The inventory screen opened or closed, client to server (ADR-0012): the server cannot see a screen, and
 * it works out which recipes are Ready only while this player is looking at one.
 */
public record InventoryWatchPacket(boolean open) implements CustomPacketPayload {

    public static final Type<InventoryWatchPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "inventory_watch"));

    public static final StreamCodec<ByteBuf, InventoryWatchPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, InventoryWatchPacket::open,
            InventoryWatchPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(InventoryWatchPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ReadyWatch.watch(player, packet.open());
        }
    }
}
