// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/** A recipe is Locked if any configured source or any hook says so (GLOSSARY.md, Lock source). */
class LocksTest {

    private static final Predicate<String> NONE = recipe -> false;
    private static final List<Predicate<String>> NO_SOURCES = List.of();

    @Test
    void nothingIsLockedWithNoSourceAndNoHooks() {
        assertFalse(Locks.of(NO_SOURCES, List.of()).test("pick"));
    }

    @Test
    void theConfiguredSourceAloneLocks() {
        Predicate<String> locked = Locks.of(List.of(Set.of("pick")::contains), List.of());
        assertTrue(locked.test("pick"));
        assertFalse(locked.test("axe"));
    }

    @Test
    void anyOneHookLocks() {
        Predicate<String> locked = Locks.of(NO_SOURCES, List.of(NONE, Set.of("axe")::contains));
        assertTrue(locked.test("axe"));
        assertFalse(locked.test("pick"));
    }

    @Test
    void anyOneSourceLocks() {
        Predicate<String> locked = Locks.of(List.of(NONE, Set.of("pick")::contains), List.of());
        assertTrue(locked.test("pick"));
        assertFalse(locked.test("axe"));
    }

    @Test
    void theSourceAndAHookLockWhatEitherLocks() {
        Predicate<String> locked = Locks.of(List.of(Set.of("pick")::contains), List.of(Set.of("axe")::contains));
        assertTrue(locked.test("pick"));
        assertTrue(locked.test("axe"));
        assertFalse(locked.test("shovel"));
    }

    private static Function<String, Optional<Locks.Lock<String>>> saying(String locks, String reason) {
        return recipe -> recipe.equals(locks) ? Optional.of(new Locks.Lock<>(reason)) : Optional.empty();
    }

    @Test
    void aReasonedHookLocksAndItsReasonIsKeptByRecipeId() {
        Locks.Reasoned<String> locks = Locks.reasoned(NO_SOURCES, List.of(), List.of(saying("axe", "Research: Steel Axe")));
        assertTrue(locks.test("axe"));
        assertFalse(locks.test("pick"));
        assertEquals(Map.of("axe", "Research: Steel Axe"), locks.reasons());
    }

    @Test
    void aReasonlessLockYieldsNoReason() {
        Locks.Reasoned<String> locks = Locks.reasoned(List.of(Set.of("pick")::contains), List.of(),
                List.of(saying("axe", null)));
        assertTrue(locks.test("pick"));
        assertTrue(locks.test("axe"));
        assertEquals(Map.of(), locks.reasons());
    }

    @Test
    void aPlainLockDoesNotHideAReasonForTheSameRecipe() {
        Locks.Reasoned<String> locks = Locks.reasoned(List.of(Set.of("axe")::contains), List.of(),
                List.of(saying("axe", "Research: Steel Axe")));
        assertTrue(locks.test("axe"));
        assertEquals(Map.of("axe", "Research: Steel Axe"), locks.reasons());
    }

    @Test
    void theFirstReasonGivenWins() {
        Locks.Reasoned<String> locks = Locks.reasoned(NO_SOURCES, List.of(),
                List.of(saying("axe", null), saying("axe", "first"), saying("axe", "second")));
        locks.test("axe");
        assertEquals(Map.of("axe", "first"), locks.reasons());
    }

    @Test
    void eachRecipeIsAskedOnce() {
        int[] asked = {0};
        Locks.Reasoned<String> locks = Locks.reasoned(NO_SOURCES, List.of(), List.of(recipe -> {
            asked[0]++;
            return Optional.of(new Locks.Lock<>("why"));
        }));
        locks.test("axe");
        locks.test("axe");
        assertEquals(1, asked[0]);
    }
}
