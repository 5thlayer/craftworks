// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.network;

import java.util.Optional;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.machine.HeldRecipeMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * An open Assembler's or Chemical Plant's Held recipe, for the screen to name and ghost its slots from.
 *
 * <p>Sent when the menu opens and again whenever the recipe changes while it is up. {@code containerId}
 * is there so one for a menu the player has since closed lands on nothing.
 */
public record AssemblerHeldPacket(int containerId, Optional<AssemblerMenu.Held> held) implements CustomPacketPayload {

    public static final Type<AssemblerHeldPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "assembler_held"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblerHeldPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AssemblerHeldPacket::containerId,
            ByteBufCodecs.optional(AssemblerMenu.Held.STREAM_CODEC), AssemblerHeldPacket::held,
            AssemblerHeldPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(AssemblerHeldPacket packet, IPayloadContext context) {
        if (context.player().containerMenu instanceof HeldRecipeMenu menu
                && context.player().containerMenu.containerId == packet.containerId()) {
            menu.show(packet.held());
        }
    }
}
