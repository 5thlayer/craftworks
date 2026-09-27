// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where another mod or a pack tells the Personal Assembler that a recipe is Locked for a player.
 *
 * <p>A recipe is Locked if the configured {@code lockSource} or any hook says so. The Assembler asks
 * every time it plans, on the server, and never learns why: a research tree, a quest or a stage are all
 * the same yes. Register once, during mod construction or common setup.
 */
public final class LockHooks {

    private static final List<LockHook> HOOKS = new CopyOnWriteArrayList<>();

    private LockHooks() {
    }

    public static void register(LockHook hook) {
        HOOKS.add(hook);
    }

    /** Every hook registered, in registration order. */
    public static List<LockHook> all() {
        return List.copyOf(HOOKS);
    }

    /** Answers whether a recipe is Locked for a player. Called on the server thread. */
    @FunctionalInterface
    public interface LockHook {
        boolean isLocked(ServerPlayer player, Identifier recipe);
    }
}
