// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.api;

/**
 * An index tab: a saved search, shown as an icon above the recipe viewer's item index (#15). Clicking it
 * writes {@code query} into the search bar, replacing what was typed.
 *
 * <p>Plain strings, so a tab needs no registry to exist: {@code id} and {@code icon} are resource
 * locations ({@code craftworks:assembling}, {@code minecraft:crafter}), {@code name} is a translation key
 * shown on hover, and {@code order} places it in the row, lowest first, ties by id.
 */
public record IndexTab(String id, String icon, String name, String query, int order) {

    public IndexTab {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("an index tab needs an id");
        if (icon == null || icon.isBlank()) throw new IllegalArgumentException("index tab " + id + " needs an icon");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("index tab " + id + " needs a name");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("index tab " + id + " needs a query");
    }
}
