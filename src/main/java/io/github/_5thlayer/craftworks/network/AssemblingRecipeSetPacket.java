// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.network;

import java.util.List;
import java.util.Set;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.assembler.AssemblingRecipeIds;
import io.github._5thlayer.craftworks.planner.AssemblingRecipeSet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Assembling recipe set's ids and the items it makes, server to client, sent on login and on every
 * {@code /reload}.
 */
public record AssemblingRecipeSetPacket(List<String> ids, List<String> itemsMade) implements CustomPacketPayload {

    public static final Type<AssemblingRecipeSetPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "assembling_recipes"));

    public static final StreamCodec<ByteBuf, AssemblingRecipeSetPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AssemblingRecipeSetPacket::ids,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AssemblingRecipeSetPacket::itemsMade,
            AssemblingRecipeSetPacket::new);

    public static AssemblingRecipeSetPacket of(AssemblingRecipeSet recipes) {
        return new AssemblingRecipeSetPacket(List.copyOf(recipes.ids()), List.copyOf(recipes.itemsMade()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(AssemblingRecipeSetPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AssemblingRecipeIds.accept(Set.copyOf(packet.ids()), Set.copyOf(packet.itemsMade())));
    }
}
