// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.network;

import java.util.List;
import java.util.Set;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.assembler.ReadyRecipeIds;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The recipe ids that are Ready for this player, server to client (ADR-0012), sent when a refresh finds
 * them different from what the client last had.
 */
public record ReadyRecipesPacket(List<String> ids) implements CustomPacketPayload {

    public static final Type<ReadyRecipesPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "ready_recipes"));

    public static final StreamCodec<ByteBuf, ReadyRecipesPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ReadyRecipesPacket::ids,
            ReadyRecipesPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ReadyRecipesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ReadyRecipeIds.accept(Set.copyOf(packet.ids())));
    }
}
