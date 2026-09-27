// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/** A recipe is Locked if the configured source or any hook says so (CONTEXT.md, Lock source). */
class LocksTest {

    private static final Predicate<String> NONE = recipe -> false;

    @Test
    void nothingIsLockedWithNoSourceAndNoHooks() {
        assertFalse(Locks.of(NONE, List.of()).test("pick"));
    }

    @Test
    void theConfiguredSourceAloneLocks() {
        Predicate<String> locked = Locks.of(Set.of("pick")::contains, List.of());
        assertTrue(locked.test("pick"));
        assertFalse(locked.test("axe"));
    }

    @Test
    void anyOneHookLocks() {
        Predicate<String> locked = Locks.of(NONE, List.of(NONE, Set.of("axe")::contains));
        assertTrue(locked.test("axe"));
        assertFalse(locked.test("pick"));
    }

    @Test
    void theSourceAndAHookLockWhatEitherLocks() {
        Predicate<String> locked = Locks.of(Set.of("pick")::contains, List.of(Set.of("axe")::contains));
        assertTrue(locked.test("pick"));
        assertTrue(locked.test("axe"));
        assertFalse(locked.test("shovel"));
    }
}
