// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;
import java.util.stream.Stream;

/** The Modules of FactoryWorks' glossary but Craftworks and Groundworks, which every Module may require (FactoryWorks ADR-0127). */
public final class OtherModules {

    // Joined at run time, so no class of the Mod holds the names craftworks_names_no_other_modules_type looks for.
    public static final List<String> NAMES = Stream.of("belt", "field", "gear", "pipe", "void", "wire").map(name -> name + "works").toList();

    /** Not a Module since FactoryWorks Core retired (FactoryWorks ADR-0128), and no Module may name it either. */
    public static final String RETIRED_PACK = String.join("", "factory", "works");

    private OtherModules() {
    }
}
