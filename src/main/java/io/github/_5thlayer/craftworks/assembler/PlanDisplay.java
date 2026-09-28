// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github._5thlayer.craftworks.planner.ItemAmount;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * A resolved plan as the Crafting Plan screen shows it (#7): what it spends, what it makes on the way,
 * and what is Missing and Locked.
 *
 * <p>{@code locked} is separate from {@code missing} on purpose: one is fixed by unlocking and the other
 * by gathering, and folding them together sends a player hunting for an item they cannot yet make.
 *
 * <p>{@code consume} is the Resolver's raw cost, and it is shown first because queueing pays all of it
 * at once: a screen listing only what will be made asks the player to commit items they were never shown.
 *
 * <p>{@code lockReasons} is why a {@code locked} item is Locked, by item, where the Lock source said
 * (#18): text for the player, such as "Research: Steel Axe", which nothing on either side reads. An
 * item without one is plain Locked.
 */
public record PlanDisplay(
        Identifier recipe,
        List<ItemAmount> consume,
        List<ItemAmount> toCraft,
        List<ItemAmount> missing,
        List<ItemAmount> locked,
        Map<String, Component> lockReasons,
        boolean complete) {

    private static final StreamCodec<ByteBuf, ItemAmount> ITEM_AMOUNT = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ItemAmount::item,
            ByteBufCodecs.VAR_INT, ItemAmount::count,
            ItemAmount::new);

    private static final StreamCodec<ByteBuf, List<ItemAmount>> AMOUNTS = ITEM_AMOUNT.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, PlanDisplay> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, PlanDisplay::recipe,
            AMOUNTS, PlanDisplay::consume,
            AMOUNTS, PlanDisplay::toCraft,
            AMOUNTS, PlanDisplay::missing,
            AMOUNTS, PlanDisplay::locked,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8,
                    ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC), PlanDisplay::lockReasons,
            ByteBufCodecs.BOOL, PlanDisplay::complete,
            PlanDisplay::new);

    public PlanDisplay {
        consume = List.copyOf(consume);
        toCraft = List.copyOf(toCraft);
        missing = List.copyOf(missing);
        locked = List.copyOf(locked);
        lockReasons = Map.copyOf(lockReasons);
    }
}
