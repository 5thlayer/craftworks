// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.network;

import java.util.function.IntFunction;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.assembler.FillRequest;
import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ByIdMap;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A recipe viewer's Fill Recipe was pressed on the inventory screen (ADR-0004).
 *
 * <p>It carries the request, not a count: one, five, all or the plan. How many "all" is, and whether
 * the inventory covers the rest, is the server's to resolve.
 */
public record FillRecipePacket(Identifier recipe, FillRequest request) implements CustomPacketPayload {

    public static final Type<FillRecipePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "fill_recipe"));

    /** Clamped, so an out-of-range id from a client reads as the plan: the request that spends nothing. */
    private static final IntFunction<FillRequest> REQUEST_BY_ID =
            ByIdMap.continuous(FillRequest::ordinal, FillRequest.values(), ByIdMap.OutOfBoundsStrategy.CLAMP);

    public static final StreamCodec<ByteBuf, FillRecipePacket> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, FillRecipePacket::recipe,
            ByteBufCodecs.idMapper(REQUEST_BY_ID, FillRequest::ordinal), FillRecipePacket::request,
            FillRecipePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(FillRecipePacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PersonalAssembler.fill(player, packet.recipe(), packet.request());
        }
    }
}
