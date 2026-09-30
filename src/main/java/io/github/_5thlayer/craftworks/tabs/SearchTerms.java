// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.tabs;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether an index tab is lit: every term of its query is among the search's terms, in any order and
 * alongside others (#15). The tab row keeps no state of its own, so a query the player typed lights its
 * tab exactly as a click does.
 *
 * <p>A term is a run of non-blank characters, compared whole and without regard to case, as EMI
 * compares them.
 */
public final class SearchTerms {

    private SearchTerms() {
    }

    public static boolean lit(String query, String search) {
        Set<String> wanted = terms(query);
        return !wanted.isEmpty() && terms(search).containsAll(wanted);
    }

    private static Set<String> terms(String text) {
        if (text == null) return Set.of();
        return Arrays.stream(text.strip().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term -> !term.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
