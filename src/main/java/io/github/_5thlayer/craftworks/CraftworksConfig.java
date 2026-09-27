// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Craftworks' server config, {@code craftworks-server.toml} in a world's {@code serverconfig}. */
public final class CraftworksConfig {

    /** What decides, besides any registered hook, whether a recipe is Locked for a player. */
    public enum LockSource {
        /** Nothing: only hooks lock. */
        NONE,
        /** A recipe is Locked until it is in the player's vanilla recipe book. */
        RECIPE_BOOK
    }

    static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<LockSource> LOCK_SOURCE;

    static {
        var builder = new ModConfigSpec.Builder();
        LOCK_SOURCE = builder
                .comment("What locks a recipe for a player, on top of any hook a mod registers:",
                        "NONE, or RECIPE_BOOK (Locked until the recipe is in the player's recipe book).")
                .defineEnum("lockSource", LockSource.NONE);
        SPEC = builder.build();
    }

    private CraftworksConfig() {
    }

    /** The configured source; {@link LockSource#NONE} until a world's config is loaded. */
    public static LockSource lockSource() {
        return SPEC.isLoaded() ? LOCK_SOURCE.get() : LockSource.NONE;
    }
}
