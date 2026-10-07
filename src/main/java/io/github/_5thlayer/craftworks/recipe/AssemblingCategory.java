// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * An Assembling recipe's category (GLOSSARY.md): the kinds of recipe the Assembler tiers are split by, which
 * decide the tiers that may hold it. Written in recipes and in the server config by its {@link #id}.
 *
 * <p>No Minecraft game types, only DFU's codec, so a tier's defaults are unit-tested. The constants' order
 * is sent over the network, by {@link AssemblingRecipe}'s stream codec: new ones go at the end, none are
 * reordered, and only the last may be removed (as 0.6 removed {@code chemistry} and {@code oil-processing}).
 */
public enum AssemblingCategory {
    CRAFTING("crafting"),
    ADVANCED_CRAFTING("advanced-crafting"),
    CRAFTING_WITH_FLUID("crafting-with-fluid");

    /**
     * The ids Craftworks 0.6 removed, with the category a recipe naming one should use: {@code chemistry} and
     * {@code oil-processing}, both now {@code crafting-with-fluid}.
     */
    private static final Map<String, AssemblingCategory> REMOVED = Map.of(
            "chemistry", CRAFTING_WITH_FLUID,
            "oil-processing", CRAFTING_WITH_FLUID);

    /**
     * Reads an id. An unknown one fails naming it and the three that are accepted; a removed one fails naming the
     * category to use instead.
     */
    public static final Codec<AssemblingCategory> CODEC = Codec.STRING.comapFlatMap(
            id -> byId(id).<DataResult<AssemblingCategory>>map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> REMOVED.containsKey(id)
                            ? "Category '" + id + "' was removed in Craftworks 0.6: use '" + REMOVED.get(id).id() + "'"
                            : "Unknown category '" + id + "', expected one of " + ids())),
            AssemblingCategory::id);

    private final String id;

    AssemblingCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<AssemblingCategory> byId(String id) {
        return Arrays.stream(values()).filter(category -> category.id.equals(id)).findFirst();
    }

    /** Whether a config entry names one of the three: what the tiers' {@code categories} accepts. */
    public static boolean isId(Object value) {
        return value instanceof String id && byId(id).isPresent();
    }

    /** The three ids, as the config's comment and an error name them. */
    public static String ids() {
        return Arrays.stream(values()).map(AssemblingCategory::id).collect(Collectors.joining(", "));
    }
}
