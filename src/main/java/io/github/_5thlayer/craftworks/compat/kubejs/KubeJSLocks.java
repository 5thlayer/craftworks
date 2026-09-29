// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.kubejs;

import java.util.Optional;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.script.ScriptType;
import io.github._5thlayer.craftworks.api.LockHooks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code CraftworksEvents.lock}, a server-script event that locks a recipe for a player: a
 * {@link LockHooks.ReasonedLockHook} like any other, combined with the other Lock sources the same way.
 *
 * <p>It is posted each time the Assembler asks about a recipe, so a script answers afresh on every
 * resolve, as every hook does. With no script listening it is not posted at all.
 */
final class KubeJSLocks {

    static final EventGroup GROUP = EventGroup.of("CraftworksEvents");
    static final EventHandler LOCK = GROUP.server("lock", () -> LockKubeEvent.class);

    private KubeJSLocks() {
    }

    static Optional<LockHooks.Lock> lock(ServerPlayer player, Identifier recipe) {
        if (!LOCK.hasListeners()) return Optional.empty();
        LockKubeEvent event = new LockKubeEvent(player, recipe);
        EventResult result = LOCK.post(ScriptType.SERVER, event);
        // A lock that cannot be answered must not quietly read as unlocked (ADR-0010): the resolve fails.
        if (result.type() == EventResult.Type.ERROR) {
            throw new IllegalStateException("A CraftworksEvents.lock script failed answering whether " + recipe
                    + " is Locked; see the KubeJS server log");
        }
        return event.answer();
    }
}
