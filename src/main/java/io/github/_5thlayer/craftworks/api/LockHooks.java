// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.api;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where another mod or a pack tells the Personal Assembler that a recipe is Locked for a player.
 *
 * <p>A recipe is Locked if the configured {@code lockSource} or any hook says so. The Assembler asks
 * every time it plans, on the server, and never learns why in any way it acts on: a research tree, a
 * quest or a stage are all the same yes. A {@link ReasonedLockHook} may say why in text, which the
 * Crafting Plan shows the player beside the Locked entry and nothing else reads. Register once, during
 * mod construction or common setup.
 */
public final class LockHooks {

    private static final List<LockHook> HOOKS = new CopyOnWriteArrayList<>();
    private static final List<ReasonedLockHook> REASONED = new CopyOnWriteArrayList<>();

    private LockHooks() {
    }

    public static void register(LockHook hook) {
        HOOKS.add(hook);
    }

    public static void registerReasoned(ReasonedLockHook hook) {
        REASONED.add(hook);
    }

    /** Every hook registered, in registration order. */
    public static List<LockHook> all() {
        return List.copyOf(HOOKS);
    }

    /** Every reasoned hook registered, in registration order. */
    public static List<ReasonedLockHook> allReasoned() {
        return List.copyOf(REASONED);
    }

    /** Answers whether a recipe is Locked for a player. Called on the server thread. */
    @FunctionalInterface
    public interface LockHook {
        boolean isLocked(ServerPlayer player, Identifier recipe);
    }

    /**
     * Answers whether a recipe is Locked for a player, and may say why. Empty is unlocked. Called on the
     * server thread.
     */
    @FunctionalInterface
    public interface ReasonedLockHook {
        Optional<Lock> lock(ServerPlayer player, Identifier recipe);
    }

    /** A reasoned hook's yes: Locked, with the reason shown to the player, or none ({@code null}). */
    public record Lock(Component reason) {

        /** Locked, with nothing to say why: the Crafting Plan shows plain "Locked". */
        public static final Lock NO_REASON = new Lock(null);

        public static Optional<Lock> because(Component reason) {
            return Optional.of(new Lock(reason));
        }
    }
}
