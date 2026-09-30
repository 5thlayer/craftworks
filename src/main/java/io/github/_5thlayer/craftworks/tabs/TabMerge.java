// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.tabs;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github._5thlayer.craftworks.api.IndexTab;

/**
 * The index tab row, from what the Java API registered and what the resource packs' tab files say (#15).
 *
 * <p>Registered tabs go in first, a later one replacing an earlier at the same id; then each file
 * replaces the tab at its id, or removes it when disabled. Files always come second, so a file beats the
 * Java API whichever loaded first.
 */
public final class TabMerge {

    private static final Comparator<IndexTab> ROW_ORDER =
            Comparator.comparingInt(IndexTab::order).thenComparing(IndexTab::id);

    private TabMerge() {
    }

    public static List<IndexTab> merge(List<IndexTab> registered, List<TabFile> files) {
        Map<String, IndexTab> byId = new LinkedHashMap<>();
        registered.forEach(tab -> byId.put(tab.id(), tab));
        for (TabFile file : files) {
            if (file.enabled()) {
                byId.put(file.id(), file.tab());
            } else {
                byId.remove(file.id());
            }
        }
        return byId.values().stream().sorted(ROW_ORDER).toList();
    }
}
