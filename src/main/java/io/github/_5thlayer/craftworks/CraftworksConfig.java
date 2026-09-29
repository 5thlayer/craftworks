// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Craftworks' server config, {@code craftworks-server.toml} in a world's {@code serverconfig}. */
public final class CraftworksConfig {

    /**
     * What can lock a recipe for a player, besides any registered hook (CONTEXT.md, Lock source).
     * Named as a pack author writes them in the TOML.
     */
    public enum LockSource {
        /** Locked until the recipe is in the player's vanilla recipe book. */
        recipeBook,
        /** Locked while Researchd says the recipe is blocked for the player's team (ADR-0010). */
        researchd
    }

    static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> LOCK_SOURCES;

    public static final ModConfigSpec.BooleanValue VANILLA_RECIPES;

    static {
        var builder = new ModConfigSpec.Builder();
        LOCK_SOURCES = builder
                .comment("What locks a recipe for a player, on top of any hook a mod registers. A recipe is",
                        "Locked if any listed source says so. Empty (the default) locks nothing.",
                        "  recipeBook: Locked until the recipe is in the player's recipe book.",
                        "  researchd:  Locked while Researchd blocks the recipe for the player's team.",
                        "For example [\"researchd\"].")
                .defineListAllowEmpty("lockSources", List.of(), () -> LockSource.researchd.name(),
                        value -> value instanceof String name
                                && Arrays.stream(LockSource.values()).anyMatch(source -> source.name().equals(name)));
        VANILLA_RECIPES = builder
                .comment("Whether vanilla's crafting recipes are Assembling recipes, from the pack built into the",
                        "mod. Off, they stay at the crafting table and a pack ships its own Assembling recipes.")
                .define("vanillaRecipes", true);
        SPEC = builder.build();
    }

    private CraftworksConfig() {
    }

    /** Whether the built-in vanilla pack is on; its default, on, until a world's config is loaded. */
    public static boolean vanillaRecipes() {
        return !SPEC.isLoaded() || VANILLA_RECIPES.get();
    }

    /** The configured sources, each once; none until a world's config is loaded. */
    public static List<LockSource> lockSources() {
        if (!SPEC.isLoaded()) return List.of();
        List<LockSource> sources = new ArrayList<>();
        for (String name : LOCK_SOURCES.get()) {
            LockSource source = LockSource.valueOf(name);
            if (!sources.contains(source)) sources.add(source);
        }
        return sources;
    }
}
