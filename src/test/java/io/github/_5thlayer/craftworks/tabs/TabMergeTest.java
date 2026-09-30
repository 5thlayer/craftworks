// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.tabs;

import io.github._5thlayer.craftworks.api.IndexTab;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The tab row from the tabs the Java API registered and the resource packs' tab files: a file replaces
 * the tab at its id, {@code enabled: false} removes it, and a file beats the Java API (#15).
 */
class TabMergeTest {

    private static final IndexTab ASSEMBLING =
            new IndexTab("craftworks:assembling", "minecraft:crafter", "craftworks.tab.assembling", "@craftworks", 0);
    private static final IndexTab TOOLS =
            new IndexTab("pack:tools", "minecraft:iron_pickaxe", "pack.tab.tools", "#c:tools", 10);

    private static List<String> ids(List<IndexTab> tabs) {
        return tabs.stream().map(IndexTab::id).toList();
    }

    @Test
    void withNoFilesTheRegisteredTabsAreTheRow() {
        assertEquals(List.of(ASSEMBLING, TOOLS), TabMerge.merge(List.of(TOOLS, ASSEMBLING), List.of()));
    }

    @Test
    void aFileAddsATab() {
        assertEquals(List.of(ASSEMBLING, TOOLS), TabMerge.merge(List.of(ASSEMBLING), List.of(TabFile.of(TOOLS))));
    }

    @Test
    void aFileReplacesTheTabAtItsId() {
        IndexTab replaced = new IndexTab("craftworks:assembling", "minecraft:stick", "pack.tab.assembled", "@craftworks stick", 5);

        assertEquals(List.of(replaced), TabMerge.merge(List.of(ASSEMBLING), List.of(TabFile.of(replaced))));
    }

    @Test
    void aDisabledFileRemovesTheTabAtItsId() {
        assertEquals(List.of(TOOLS),
                TabMerge.merge(List.of(ASSEMBLING, TOOLS), List.of(TabFile.disabled("craftworks:assembling"))));
    }

    @Test
    void aDisabledFileForATabNobodyRegisteredRemovesNothing() {
        assertEquals(List.of(ASSEMBLING), TabMerge.merge(List.of(ASSEMBLING), List.of(TabFile.disabled("pack:nothing"))));
    }

    /** The Java API registers whenever it likes, even after the files load; the file still wins. */
    @Test
    void aFileBeatsTheJavaApiOnAnIdClash() {
        IndexTab fromFile = new IndexTab("pack:tools", "minecraft:diamond_pickaxe", "pack.tab.tools", "#c:tools/mining_tool", 10);

        assertEquals(List.of(ASSEMBLING, fromFile),
                TabMerge.merge(List.of(ASSEMBLING, TOOLS), List.of(TabFile.of(fromFile))));
    }

    @Test
    void aSecondRegistrationAtAnIdReplacesTheFirst() {
        IndexTab again = new IndexTab("craftworks:assembling", "minecraft:crafter", "craftworks.tab.assembling", "@craftworks", 3);

        assertEquals(List.of(again), TabMerge.merge(List.of(ASSEMBLING, again), List.of()));
    }

    @Test
    void tabsAreOrderedByOrderThenById() {
        IndexTab b = new IndexTab("pack:b", "minecraft:stone", "b", "b", 1);
        IndexTab a = new IndexTab("pack:a", "minecraft:stone", "a", "a", 1);
        IndexTab first = new IndexTab("pack:z", "minecraft:stone", "z", "z", -1);

        assertEquals(List.of("pack:z", "pack:a", "pack:b"), ids(TabMerge.merge(List.of(b, a), List.of(TabFile.of(first)))));
    }
}
