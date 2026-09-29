// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.kubejs;

import java.util.Optional;

import dev.latvian.mods.kubejs.player.KubePlayerEvent;
import io.github._5thlayer.craftworks.api.LockHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a {@code CraftworksEvents.lock} script is handed: the player, the recipe asked about, and
 * {@code lock()} or {@code lock(reason)} to lock it. Not calling either leaves it to the other sources.
 * Several listeners may lock; the last reason given is the one shown, and a plain {@code lock()} never
 * takes an earlier reason away.
 */
public final class LockKubeEvent implements KubePlayerEvent {

    private final ServerPlayer player;
    private final Identifier recipe;
    private Optional<LockHooks.Lock> answer = Optional.empty();

    LockKubeEvent(ServerPlayer player, Identifier recipe) {
        this.player = player;
        this.recipe = recipe;
    }

    @Override
    public ServerPlayer getEntity() {
        return player;
    }

    /** The Assembling recipe's id. */
    public Identifier getRecipe() {
        return recipe;
    }

    /** Locked, with nothing to say why. */
    public void lock() {
        if (answer.isEmpty()) answer = Optional.of(LockHooks.Lock.NO_REASON);
    }

    /** Locked, with the reason the Crafting Plan shows beside the entry. */
    public void lock(Component reason) {
        answer = LockHooks.Lock.because(reason);
    }

    Optional<LockHooks.Lock> answer() {
        return answer;
    }
}
