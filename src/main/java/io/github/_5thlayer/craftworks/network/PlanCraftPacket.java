// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.network;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Crafting Plan's {@code +1}, {@code +5} or {@code all}: resolve this many and queue it now (#7).
 *
 * <p>A recipe and a count, not a plan id. The server resolves afresh against the inventory as it is at
 * the click and takes the cost in the same step, so there is no held plan for a stale click to pay for.
 */
public record PlanCraftPacket(Identifier recipe, int crafts) implements CustomPacketPayload {

    public static final Type<PlanCraftPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "plan_craft"));

    public static final StreamCodec<ByteBuf, PlanCraftPacket> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, PlanCraftPacket::recipe,
            ByteBufCodecs.VAR_INT, PlanCraftPacket::crafts,
            PlanCraftPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(PlanCraftPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PersonalAssembler.craft(player, packet.recipe(), packet.crafts());
        }
    }
}
