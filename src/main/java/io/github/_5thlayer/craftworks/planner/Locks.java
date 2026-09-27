// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The Lock source as the Resolver asks it: one predicate over recipe ids, for one player.
 *
 * <p>A recipe is Locked if the configured source or any registered hook says so. Nothing here knows
 * why, and neither does the Resolver: a hook is a yes or a no, never a reason (CONTEXT.md).
 */
public final class Locks {

    private Locks() {
    }

    public static Predicate<String> of(Predicate<String> source, List<? extends Predicate<String>> hooks) {
        List<Predicate<String>> all = new ArrayList<>(hooks.size() + 1);
        all.add(source);
        all.addAll(hooks);
        return recipe -> {
            for (Predicate<String> lock : all) {
                if (lock.test(recipe)) return true;
            }
            return false;
        };
    }
}
