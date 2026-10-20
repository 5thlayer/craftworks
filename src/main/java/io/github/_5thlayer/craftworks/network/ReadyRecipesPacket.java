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
 * The recipe ids that are Ready for this player, server to client (ADR-0012), sent when a pass ends and
 * finds them different from what the client last had.
 *
 * <p>{@code additive} says the ids are only ones a running pass has newly found, to add to the client's
 * set; without it they are the whole set and replace it (ADR-0024). The pass ends with the second kind,
 * which is what removes a recipe, so none vanishes and returns mid-pass. Additive packets are sent
 * unconditionally, each already holding only ids the client lacks.
 */
public record ReadyRecipesPacket(List<String> ids, boolean additive) implements CustomPacketPayload {

    public static final Type<ReadyRecipesPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "ready_recipes"));

    public static final StreamCodec<ByteBuf, ReadyRecipesPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ReadyRecipesPacket::ids,
            ByteBufCodecs.BOOL, ReadyRecipesPacket::additive,
            ReadyRecipesPacket::new);

    /** The whole set, replacing the client's. */
    public ReadyRecipesPacket(List<String> ids) {
        this(ids, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ReadyRecipesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.additive()) {
                ReadyRecipeIds.add(Set.copyOf(packet.ids()));
            } else {
                ReadyRecipeIds.accept(Set.copyOf(packet.ids()));
            }
        });
    }
}
