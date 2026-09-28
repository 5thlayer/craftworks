// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The Lock source as the Resolver asks it: one predicate over recipe ids, for one player.
 *
 * <p>A recipe is Locked if any configured source or any registered hook says so. The Resolver never
 * knows why: a hook is a yes or a no to it. A reasoned lock may give a reason with its yes, and the
 * reasons are kept beside the predicate, by recipe id, for the Crafting Plan to show (CONTEXT.md). The
 * reason is text relayed to the player, never something the Assembler acts on.
 */
public final class Locks {

    private Locks() {
    }

    public static Predicate<String> of(
            List<? extends Predicate<String>> sources, List<? extends Predicate<String>> hooks) {
        return reasoned(sources, hooks, List.<Function<String, Optional<Lock<Object>>>>of());
    }

    /**
     * The Lock source with reasons. Every reasoned lock is asked, even of a recipe a plain one already
     * locks, so a reason is not lost to whichever lock happened to answer first.
     */
    public static <R> Reasoned<R> reasoned(List<? extends Predicate<String>> sources,
            List<? extends Predicate<String>> hooks,
            List<? extends Function<String, Optional<Lock<R>>>> reasoning) {
        List<Predicate<String>> plain = new ArrayList<>(sources);
        plain.addAll(hooks);
        return new Reasoned<>(plain, List.copyOf(reasoning));
    }

    /** A reasoned lock's yes, with its reason or none ({@code null}). */
    public record Lock<R>(R reason) {
    }

    /**
     * The predicate the Resolver asks, remembering each answer: a recipe is asked of its locks once,
     * and its reason, if any lock gave one, is in {@link #reasons()} after.
     */
    public static final class Reasoned<R> implements Predicate<String> {

        private final List<Predicate<String>> plain;
        private final List<Function<String, Optional<Lock<R>>>> reasoning;
        private final Map<String, Boolean> answers = new HashMap<>();
        private final Map<String, R> reasons = new LinkedHashMap<>();

        private Reasoned(List<Predicate<String>> plain, List<Function<String, Optional<Lock<R>>>> reasoning) {
            this.plain = plain;
            this.reasoning = reasoning;
        }

        @Override
        public boolean test(String recipe) {
            return answers.computeIfAbsent(recipe, this::ask);
        }

        private boolean ask(String recipe) {
            boolean locked = false;
            for (Function<String, Optional<Lock<R>>> lock : reasoning) {
                Optional<Lock<R>> answer = lock.apply(recipe);
                if (answer.isEmpty()) continue;
                locked = true;
                R reason = answer.get().reason();
                if (reason != null) {
                    reasons.put(recipe, reason);
                    return true;
                }
            }
            if (locked) return true;
            for (Predicate<String> lock : plain) {
                if (lock.test(recipe)) return true;
            }
            return false;
        }

        /** The reason each Locked recipe asked so far was given, by recipe id; a reasonless one is absent. */
        public Map<String, R> reasons() {
            return Map.copyOf(reasons);
        }
    }
}
