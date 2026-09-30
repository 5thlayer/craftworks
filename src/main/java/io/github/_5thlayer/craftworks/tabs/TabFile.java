// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.tabs;

import io.github._5thlayer.craftworks.api.IndexTab;

/**
 * One resource pack tab file, {@code assets/<namespace>/craftworks/tabs/<id>.json}: the tab it puts at
 * {@code id}, or none ({@code tab} null) when it says {@code "enabled": false}.
 */
public record TabFile(String id, IndexTab tab) {

    public TabFile {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("a tab file needs an id");
        if (tab != null && !tab.id().equals(id)) {
            throw new IllegalArgumentException("tab file " + id + " holds tab " + tab.id());
        }
    }

    public static TabFile of(IndexTab tab) {
        return new TabFile(tab.id(), tab);
    }

    public static TabFile disabled(String id) {
        return new TabFile(id, null);
    }

    public boolean enabled() {
        return tab != null;
    }
}
