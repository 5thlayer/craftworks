// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.tabs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tab is lit while every term of its query is among the search's terms (#15). */
class SearchTermsTest {

    @Test
    void theTabsOwnQueryLightsIt() {
        assertTrue(SearchTerms.lit("@craftworks", "@craftworks"));
    }

    @Test
    void termsTypedAfterOrBeforeTheQueryKeepItLit() {
        assertTrue(SearchTerms.lit("@craftworks", "@craftworks stick"));
        assertTrue(SearchTerms.lit("@craftworks", "stick @craftworks"));
        assertTrue(SearchTerms.lit("@craftworks", "  stick   @craftworks  "));
    }

    @Test
    void aQueryOfSeveralTermsNeedsThemAllInAnyOrder() {
        assertTrue(SearchTerms.lit("@craftworks #minecraft:logs", "#minecraft:logs oak @craftworks"));
        assertFalse(SearchTerms.lit("@craftworks #minecraft:logs", "@craftworks oak"));
    }

    @Test
    void aTermIsMatchedWholeNotAsAPrefix() {
        assertFalse(SearchTerms.lit("@craftworks", "@craft"));
        assertFalse(SearchTerms.lit("@craftworks", "@craftworksx"));
        assertFalse(SearchTerms.lit("@craftworks", "-@craftworks"));
    }

    /** EMI searches without regard to case, so the tab lights the same way. */
    @Test
    void caseDoesNotMatter() {
        assertTrue(SearchTerms.lit("@craftworks", "@CraftWorks"));
    }

    @Test
    void anEmptySearchLightsNothing() {
        assertFalse(SearchTerms.lit("@craftworks", ""));
        assertFalse(SearchTerms.lit("@craftworks", null));
    }

    @Test
    void aBlankQueryIsNeverLit() {
        assertFalse(SearchTerms.lit("  ", "stick"));
    }
}
