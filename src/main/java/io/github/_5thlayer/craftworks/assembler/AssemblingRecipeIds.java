// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.Set;

/**
 * The client's copy of which recipe ids are in the Assembling recipe set, so a recipe viewer knows which
 * recipes the Assembler claims before anything is clicked, and of the items those recipes make, which
 * EMI's {@code @craftworks} lists (#15).
 *
 * <p>Ids only. What a plan costs, what is Missing and what is Locked stay on the server, where they are
 * resolved per player.
 *
 * <p>Empty until the first sync, and an empty set claims nothing. Free of {@code net.minecraft.client},
 * so a dedicated server can load the packet's handler.
 */
public final class AssemblingRecipeIds {

    private static volatile Set<String> ids = Set.of();
    private static volatile Set<String> itemsMade = Set.of();
    private static volatile Runnable onSync = () -> { };

    private AssemblingRecipeIds() {
    }

    public static void accept(Set<String> syncedIds, Set<String> syncedItemsMade) {
        ids = Set.copyOf(syncedIds);
        itemsMade = Set.copyOf(syncedItemsMade);
        onSync.run();
    }

    public static boolean contains(String recipeId) {
        return ids.contains(recipeId);
    }

    /** Whether some Assembling recipe makes this item, named by its bare registry id. */
    public static boolean makes(String itemId) {
        return itemsMade.contains(itemId);
    }

    public static int size() {
        return ids.size();
    }

    /**
     * Run after every sync, on the client thread: a recipe viewer that searched before the set arrived
     * searches again. One listener; a later call replaces it.
     */
    public static void onSync(Runnable listener) {
        onSync = listener;
    }
}
