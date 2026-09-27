// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import io.github._5thlayer.craftworks.planner.AssemblerQueue;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Runs every player's queue, screen open or not.
 *
 * <p>That is the point of a queue rather than a crafting grid: the plan was paid for when it was queued,
 * so it keeps going while the player walks away, and it is still going when they come back. The
 * inventory screen is a view of it, never the thing driving it.
 *
 * <p>The queue is re-synced on a slow beat rather than every tick. A progress bar does not need twenty
 * updates a second, and the queue's own truth is on the server either way.
 */
final class AssemblerTicker {

    /** Four times a second, which is smooth enough for a bar and cheap enough for a full server. */
    private static final int SYNC_INTERVAL_TICKS = 5;

    private AssemblerTicker() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(AssemblerTicker::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(AssemblerTicker::onLogin);
        // Last, so a mod that cancels the death (a revive, a totem of its own) has had its say first.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AssemblerTicker::onDeath);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AssemblerQueue queue = PersonalAssembler.queueOf(player);
        boolean wasEmpty = queue.isEmpty();
        PersonalAssembler.tick(player);
        if (wasEmpty && queue.isEmpty()) return;
        if (player.tickCount % SYNC_INTERVAL_TICKS == 0 || queue.isEmpty()) {
            PersonalAssembler.sync(player);
        }
    }

    /** A player who logs in gets the queue they left running. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PersonalAssembler.sync(player);
        }
    }

    /** Refunded before the items drop, so what the queue held drops with them. */
    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PersonalAssembler.refundAll(player);
        }
    }
}
