// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github._5thlayer.craftworks.network.ReadyRecipesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The players whose clients report the inventory screen open, and the Ready set each is sent (ADR-0012).
 *
 * <p>The server cannot see a screen open, so the client says so ({@code InventoryWatchPacket}). A player
 * who is not in here costs nothing: this class looks up their id on their tick and stops.
 *
 * <p>No event fires when an inventory changes, so a watched player's {@code Inventory.getTimesChanged()}
 * is compared each tick and a difference is handed to {@link ReadyRefresh}, which debounces it. A
 * completing plan puts items in the inventory, so it counts as a change like a pickup.
 */
public final class ReadyWatch {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftworks");

    private static final Map<UUID, Watch> WATCHED = new HashMap<>();

    private ReadyWatch() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ReadyWatch::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ReadyWatch::onLogout);
    }

    /** The client reported the inventory screen open or closed. */
    public static void watch(ServerPlayer player, boolean open) {
        if (!open) {
            Watch closed = WATCHED.get(player.getUUID());
            if (closed != null) closed.refresh.close();
            return;
        }
        Watch watch = WATCHED.computeIfAbsent(player.getUUID(), id -> new Watch(player));
        watch.player = player;
        watch.seenChanges = player.getInventory().getTimesChanged();
        watch.refresh.open();
    }

    /** Whether this player's client says the inventory screen is open, which is when Ready is worked out. */
    public static boolean watching(ServerPlayer player) {
        Watch watch = WATCHED.get(player.getUUID());
        return watch != null && watch.refresh.watching();
    }

    /** The Ready set last sent to this player, or empty if none was: what their client lists. */
    public static Set<String> published(ServerPlayer player) {
        Watch watch = WATCHED.get(player.getUUID());
        return watch == null ? Set.of() : watch.published;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Watch watch = WATCHED.get(player.getUUID());
        if (watch == null || !watch.refresh.watching()) return;
        // A respawn is a new ServerPlayer under the same id.
        watch.player = player;
        int changes = player.getInventory().getTimesChanged();
        if (changes != watch.seenChanges) {
            watch.seenChanges = changes;
            watch.refresh.inventoryChanged();
        }
        watch.refresh.tick();
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WATCHED.remove(event.getEntity().getUUID());
    }

    /** One watched player. */
    private static final class Watch {
        final ReadyRefresh refresh;
        ServerPlayer player;
        int seenChanges;
        Set<String> published = Set.of();
        boolean sentOnce;

        Watch(ServerPlayer player) {
            this.player = player;
            this.refresh = new ReadyRefresh(System::nanoTime, () -> ReadyRecipes.begin(this.player), this::publish);
        }

        /** Sent when it differs from what the client already has: the client keeps the last set between openings. */
        private void publish(ReadyRefresh.Refreshed done) {
            LOGGER.debug("Craftworks: Ready for {} worked out in {} ms over {} tick(s): {} of {} recipes",
                    player.getGameProfile().name(), done.nanos() / 1_000_000.0, done.ticks(), done.ready().size(), done.total());
            if (sentOnce && published.equals(done.ready())) return;
            sentOnce = true;
            published = done.ready();
            List<String> ids = new ArrayList<>(published);
            Collections.sort(ids);
            PacketDistributor.sendToPlayer(player, new ReadyRecipesPacket(ids));
        }
    }
}
