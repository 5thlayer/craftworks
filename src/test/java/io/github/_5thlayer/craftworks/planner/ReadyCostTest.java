// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * What a Ready pass costs over a generated set shaped like a big pack's worst (#67), counted in recipe
 * applications tried and never in time, which CI cannot hold still. {@link #measure} prints the times
 * for a human to read.
 *
 * <p>{@link #measure} is tagged {@code benchmark} and left out of {@code build}; run it by hand with
 * {@code sh ./gradlew test -PwithBenchmarks --tests '*ReadyCostTest.measure' -i}.
 */
class ReadyCostTest {

    /** The most applications any one resolve of the big set may try with the memo. */
    private static final long WORK_PER_RESOLVE = 4;
    /** The most a whole pass over the big set may try with the memo. */
    private static final long WORK_PER_PASS = 400;

    /** The most applications any one resolve of the recolouring set may try with the memo. */
    private static final long RECOLOUR_WORK_PER_RESOLVE = 4;
    /** The most a whole pass over the recolouring set may try with the memo. */
    private static final long RECOLOUR_WORK_PER_PASS = 1_000;

    private record Sample(String id, long nanos, long applications, boolean cutShort) {
    }

    private static List<String> sorted(AssemblingRecipeSet recipes) {
        List<String> ids = new ArrayList<>(recipes.ids());
        ids.sort(null);
        return ids;
    }

    private static List<Sample> pass(AssemblingRecipeSet recipes, ItemBag stock, Predicate<String> locked, boolean memo) {
        Resolver resolver = new Resolver(recipes, locked);
        Resolver.Memo remembered = resolver.memo(stock);
        List<Sample> samples = new ArrayList<>();
        for (String id : sorted(recipes)) {
            long before = resolver.applicationsTried();
            int cut = resolver.searchesCutShort();
            long start = System.nanoTime();
            boolean ready = (memo ? resolver.resolveReady(id, remembered) : resolver.resolve(id, 1, stock)).complete();
            samples.add(new Sample(ready ? id + " (ready)" : id, System.nanoTime() - start,
                    resolver.applicationsTried() - before, resolver.searchesCutShort() > cut));
        }
        return samples;
    }

    private static Set<String> readySet(AssemblingRecipeSet recipes, ItemBag stock, Predicate<String> locked, boolean memo) {
        return new TreeSet<>(pass(recipes, stock, locked, memo).stream()
                .map(Sample::id).filter(id -> id.endsWith(" (ready)")).toList());
    }

    @Test
    @Tag("benchmark")
    void measure() {
        AssemblingRecipeSet recipes = BigRecipes.build(7, 6, 3, true);
        ItemBag stock = BigRecipes.stock(36, 0, 2);
        for (boolean memo : new boolean[] {false, true}) {
            // Warm up the JIT, then the pass that is reported.
            for (int round = 0; round < 3; round++) pass(recipes, stock, id -> false, memo);
            System.out.println(memo ? "WITH MEMO" : "WITHOUT MEMO");
            report(pass(recipes, stock, id -> false, memo));
        }
    }

    @Test
    void theMemoLeavesTheReadySetExactlyAsItWas() {
        for (int[] shape : new int[][] {{2, 3, 2, 0}, {3, 4, 2, 0}, {4, 3, 2, 0}, {3, 3, 3, 0}, {2, 3, 2, 2}}) {
            AssemblingRecipeSet recipes = BigRecipes.build(shape[0], shape[1], shape[2], true, shape[3], 4);
            ItemBag recoloured = BigRecipes.stock(3, 1, 2);
            recoloured.add(BigRecipes.base(0), 1);
            recoloured.add(BigRecipes.dye(1), 1);
            recoloured.add(BigRecipes.dye(3), 1);
            recoloured.add(BigRecipes.coloured(1, 2), 1);
            for (ItemBag stock : List.of(BigRecipes.stock(5), BigRecipes.stock(36, 0, 2), BigRecipes.stock(3, 1, 2, 3, 4),
                    BigRecipes.stock(3, 0, 1, 2, 3, 4, 5), recoloured)) {
                for (Predicate<String> locked : List.<Predicate<String>>of(id -> false, id -> id.hashCode() % 5 == 0)) {
                    Resolver unmemoed = new Resolver(recipes, locked);
                    for (String id : sorted(recipes)) unmemoed.resolve(id, 1, stock);
                    assertEquals(0, unmemoed.searchesCutShort(),
                            "the set is too big for the comparison: a search was cut short without the memo");
                    Set<String> without = readySet(recipes, stock, locked, false);
                    Set<String> with = readySet(recipes, stock, locked, true);
                    assertEquals(without, with, "the memo changed what is Ready for shape " + List.of(shape[0], shape[1], shape[2], shape[3]));
                }
            }
        }
    }

    @Test
    void theMemoMakesEveryResolveOfTheRecolouringSetCheap() {
        // Forty items in sixteen colours, none of it held, no dye held either (#70).
        AssemblingRecipeSet recipes = BigRecipes.build(0, 0, 0, false, 40, BigRecipes.COLOURS);
        ItemBag stock = BigRecipes.stock(36);
        List<Sample> with = pass(recipes, stock, id -> false, true);
        long worst = with.stream().mapToLong(Sample::applications).max().orElseThrow();
        long total = with.stream().mapToLong(Sample::applications).sum();
        assertTrue(worst <= RECOLOUR_WORK_PER_RESOLVE, "a resolve tried " + worst + " applications");
        assertTrue(total <= RECOLOUR_WORK_PER_PASS, "the pass tried " + total + " applications");
        assertEquals(0, with.stream().filter(Sample::cutShort).count(), "a search was cut short");
        assertTrue(pass(recipes, stock, id -> false, false).stream().anyMatch(Sample::cutShort),
                "without the memo the recolouring set should hit the search limit");
    }

    @Test
    void theMemoMakesEveryResolveOfTheBigSetCheap() {
        AssemblingRecipeSet recipes = BigRecipes.build(7, 6, 3, true);
        ItemBag stock = BigRecipes.stock(36);
        List<Sample> with = pass(recipes, stock, id -> false, true);
        long worst = with.stream().mapToLong(Sample::applications).max().orElseThrow();
        long total = with.stream().mapToLong(Sample::applications).sum();
        assertTrue(worst <= WORK_PER_RESOLVE, "a resolve tried " + worst + " applications");
        assertTrue(total <= WORK_PER_PASS, "the pass tried " + total + " applications");
        assertEquals(0, with.stream().filter(Sample::cutShort).count(), "a search was cut short");
        long without = pass(recipes, stock, id -> false, false).stream().mapToLong(Sample::applications).sum();
        assertTrue(without > 20 * total, "the memo should be worth at least 20x here: " + without + " against " + total);
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
        System.out.printf("  max = %.3f ms, max applications = %d%n", byTime.get(byTime.size() - 1).nanos() / 1e6,
                samples.stream().mapToLong(Sample::applications).max().orElse(0));
        System.out.println("  over 2 ms: " + samples.stream().filter(s -> s.nanos() > 2_000_000L).count());
        for (int i = byTime.size() - 1; i >= Math.max(0, byTime.size() - 6); i--) {
            Sample s = byTime.get(i);
            System.out.printf("  slow %s %.3f ms, %d applications%s%n", s.id(), s.nanos() / 1e6, s.applications(),
                    s.cutShort() ? " (cut short)" : "");
        }
    }
}
