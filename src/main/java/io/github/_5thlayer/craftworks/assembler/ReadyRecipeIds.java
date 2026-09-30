// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.Set;

/**
 * The client's copy of which Assembling recipes are Ready for this player, as the server last sent them
 * (ADR-0012), so a recipe viewer's craftables list can ask without a round trip.
 *
 * <p>Empty until the first sync, after leaving a world and on a server without Craftworks, and an empty
 * set lists no Assembling recipe. It is the last set sent, not a live answer: the server works Ready out
 * only while the inventory screen is open, so between openings it is what the last opening ended with.
 *
 * <p>Free of {@code net.minecraft.client}, so a dedicated server can load the packet's handler.
 */
public final class ReadyRecipeIds {

    private static volatile Set<String> ids = Set.of();
    private static volatile Runnable onChange = () -> { };

    private ReadyRecipeIds() {
    }

    /** Replaces the set, and tells the listener if it differs. */
    public static void accept(Set<String> synced) {
        Set<String> next = Set.copyOf(synced);
        if (next.equals(ids)) return;
        ids = next;
        onChange.run();
    }

    /** Forgets the set, as on leaving a world. */
    public static void clear() {
        accept(Set.of());
    }

    public static boolean contains(String recipeId) {
        return ids.contains(recipeId);
    }

    public static Set<String> all() {
        return ids;
    }

    /**
     * Run after the set changes, on the client thread: a recipe viewer that worked out its craftables
     * from the old set works them out again. One listener; a later call replaces it.
     */
    public static void onChange(Runnable listener) {
        onChange = listener;
    }
}
