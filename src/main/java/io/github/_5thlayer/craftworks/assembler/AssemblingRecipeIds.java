// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.Set;

/**
 * The client's copy of which recipe ids are in the Assembling recipe set, so a recipe viewer knows which
 * recipes the Assembler claims before anything is clicked.
 *
 * <p>Ids only. What a plan costs, what is Missing and what is Locked stay on the server, where they are
 * resolved per player.
 *
 * <p>Empty until the first sync, and an empty set claims nothing. Free of {@code net.minecraft.client},
 * so a dedicated server can load the packet's handler.
 */
public final class AssemblingRecipeIds {

    private static volatile Set<String> ids = Set.of();

    private AssemblingRecipeIds() {
    }

    public static void accept(Set<String> synced) {
        ids = Set.copyOf(synced);
    }

    public static boolean contains(String recipeId) {
        return ids.contains(recipeId);
    }

    public static int size() {
        return ids.size();
    }
}
