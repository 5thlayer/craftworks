// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Where a pack or another mod adds an index tab above EMI's item index (#15).
 *
 * <p>A resource pack's {@code assets/<namespace>/craftworks/tabs/<id>.json} beats a tab registered here
 * at the same id, and can remove one with {@code "enabled": false}; a second registration at an id
 * replaces the first. Register at any time on the client; the row is worked out as it is drawn.
 */
public final class IndexTabs {

    private static final List<IndexTab> REGISTERED = new CopyOnWriteArrayList<>();

    private IndexTabs() {
    }

    public static void register(IndexTab tab) {
        REGISTERED.add(tab);
    }

    /** Every tab registered, in registration order. */
    public static List<IndexTab> all() {
        return List.copyOf(REGISTERED);
    }
}
