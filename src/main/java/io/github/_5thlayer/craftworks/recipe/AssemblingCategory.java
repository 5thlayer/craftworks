// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * An Assembling recipe's category (GLOSSARY.md): the kinds of recipe Factorio's machines are split by, which
 * decide the Assemblers that may hold it. Written in recipes and in the server config by its {@link #id}.
 *
 * <p>No Minecraft game types, only DFU's codec, so a tier's defaults are unit-tested. The constants' order
 * is sent over the network, by {@link AssemblingRecipe}'s stream codec: new ones go at the end, and none are
 * reordered.
 */
public enum AssemblingCategory {
    CRAFTING("crafting"),
    ADVANCED_CRAFTING("advanced-crafting"),
    CRAFTING_WITH_FLUID("crafting-with-fluid"),
    CHEMISTRY("chemistry"),
    OIL_PROCESSING("oil-processing");

    /** Reads an id, and fails naming it and the five that are accepted. */
    public static final Codec<AssemblingCategory> CODEC = Codec.STRING.comapFlatMap(
            id -> byId(id).<DataResult<AssemblingCategory>>map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown category '" + id + "', expected one of " + ids())),
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

    /** Whether a config entry names one of the five: what the tiers' {@code categories} accepts. */
    public static boolean isId(Object value) {
        return value instanceof String id && byId(id).isPresent();
    }

    /** The five ids, as the config's comment and an error name them. */
    public static String ids() {
        return Arrays.stream(values()).map(AssemblingCategory::id).collect(Collectors.joining(", "));
    }
}
