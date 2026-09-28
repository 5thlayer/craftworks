// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.network;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.assembler.CraftingPlanMenu;
import io.github._5thlayer.craftworks.assembler.PlanDisplay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The open Crafting Plan, re-resolved (#7).
 *
 * <p>The screen stays up while the queue spends the inventory under it, so the server sends the plan
 * again after a press and on the queue's sync beat, and the menu takes it in place. Reopening the menu
 * instead would reset the screen, and with it the cursor.
 *
 * <p>{@code containerId} is there so an update resolved for a menu the player has since closed lands on
 * nothing rather than on whatever screen replaced it.
 */
public record PlanUpdatePacket(int containerId, PlanDisplay display, int largestAffordable)
        implements CustomPacketPayload {

    public static final Type<PlanUpdatePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "plan_update"));

    public static final StreamCodec<ByteBuf, PlanUpdatePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlanUpdatePacket::containerId,
            PlanDisplay.STREAM_CODEC, PlanUpdatePacket::display,
            ByteBufCodecs.VAR_INT, PlanUpdatePacket::largestAffordable,
            PlanUpdatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(PlanUpdatePacket packet, IPayloadContext context) {
        if (context.player().containerMenu instanceof CraftingPlanMenu menu
                && menu.containerId == packet.containerId()) {
            menu.update(packet.display(), packet.largestAffordable());
        }
    }
}
