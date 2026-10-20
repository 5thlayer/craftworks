// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

/** What a Ready pass costs over a big generated set (#67). */
class ReadyCostTest {

    private record Sample(String id, long nanos, long applications, boolean cutShort) {
    }

    @Test
    void measure() {
        AssemblingRecipeSet recipes = BigRecipes.build(7, 6, 3, true);
        ItemBag stock = BigRecipes.stock(36, 0, 2);
        Resolver resolver = new Resolver(recipes, id -> false);
        List<String> ids = new ArrayList<>(recipes.ids());
        ids.sort(null);
        // Warm up the JIT on a throwaway resolver.
        Resolver warm = new Resolver(recipes, id -> false);
        for (int round = 0; round < 3; round++) for (String id : ids) warm.resolve(id, 1, stock);
        List<Sample> samples = new ArrayList<>();
        for (String id : ids) {
            long before = resolver.applicationsTried();
            int cut = resolver.searchesCutShort();
            long start = System.nanoTime();
            resolver.resolve(id, 1, stock);
            samples.add(new Sample(id, System.nanoTime() - start, resolver.applicationsTried() - before,
                    resolver.searchesCutShort() > cut));
        }
        report(samples);
    }

    static void report(List<Sample> samples) {
        List<Sample> byTime = new ArrayList<>(samples);
        byTime.sort(Comparator.comparingLong(Sample::nanos));
        long total = samples.stream().mapToLong(Sample::nanos).sum();
        long apps = samples.stream().mapToLong(Sample::applications).sum();
        System.out.printf("RESOLVES n=%d total=%.1fms applications=%d cutShort=%d%n", samples.size(), total / 1e6, apps,
                samples.stream().filter(Sample::cutShort).count());
        for (double q : new double[] {0.5, 0.9, 0.99}) {
            System.out.printf("  p%.0f = %.3f ms%n", q * 100, byTime.get((int) (q * (byTime.size() - 1))).nanos() / 1e6);
        }
        System.out.printf("  max = %.3f ms%n", byTime.get(byTime.size() - 1).nanos() / 1e6);
        System.out.println("  over 2 ms: " + samples.stream().filter(s -> s.nanos() > 2_000_000L).count());
        for (int i = byTime.size() - 1; i >= Math.max(0, byTime.size() - 6); i--) {
            Sample s = byTime.get(i);
            System.out.printf("  slow %s %.3f ms, %d applications%s%n", s.id(), s.nanos() / 1e6, s.applications(),
                    s.cutShort() ? " (cut short)" : "");
        }
    }
}
