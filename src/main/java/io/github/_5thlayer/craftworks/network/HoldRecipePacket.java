// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.network;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A recipe viewer's Fill Recipe was pressed with an Assembler open: set it as the Held recipe, or be told why not.
 * It carries the recipe and nothing else; whether this player may set it is the server's call.
 */
public record HoldRecipePacket(Identifier recipe) implements CustomPacketPayload {

    public static final Type<HoldRecipePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "hold_recipe"));

    public static final StreamCodec<ByteBuf, HoldRecipePacket> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, HoldRecipePacket::recipe,
            HoldRecipePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(HoldRecipePacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AssemblerMenu menu) {
            menu.request(player, packet.recipe());
        }
    }
}
