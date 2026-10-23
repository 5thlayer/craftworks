// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github._5thlayer.craftworks.recipe.BrewingChains.Chain;
import io.github._5thlayer.craftworks.recipe.BrewingChains.ContainerMix;
import io.github._5thlayer.craftworks.recipe.BrewingChains.ItemChain;
import io.github._5thlayer.craftworks.recipe.BrewingChains.ItemMix;
import io.github._5thlayer.craftworks.recipe.BrewingChains.PotionChain;
import io.github._5thlayer.craftworks.recipe.BrewingChains.PotionMix;
import io.github._5thlayer.craftworks.recipe.BrewingChains.Table;
import org.junit.jupiter.api.Test;

/** The chains from water that Brewed recipes fold (ADR-0025, #76), over a table of plain strings. */
class BrewingChainsTest {

    private static final List<ContainerMix<String, String>> CONTAINERS = List.of(
            new ContainerMix<>("potion", "gunpowder", "splash"),
            new ContainerMix<>("splash", "breath", "lingering"));

    private static Table<String, String, String, String> table(List<PotionMix<String, String>> potions,
            List<ItemMix<String, String, String>> items) {
        return new Table<>("water", "potion", potions, items, CONTAINERS);
    }

    private static PotionMix<String, String> mix(String from, String ingredient, String to) {
        return new PotionMix<>(from, ingredient, to);
    }

    /** A chain as one line: its ingredients, then {@code > potion in container} or {@code > item}. */
    private static String show(Chain<String, String, String, String> chain) {
        String steps = String.join("+", chain.ingredients());
        return switch (chain) {
            case PotionChain<String, String, String, String> potion -> steps + " > " + potion.potion() + " in " + potion.container();
            case ItemChain<String, String, String, String> item -> steps + " > " + item.result();
        };
    }

    private static List<String> shown(Table<String, String, String, String> table) {
        return BrewingChains.chains(table).stream().map(BrewingChainsTest::show).toList();
    }

    @Test
    void aPotionTwoChainsReachHasTwoRecipes() {
        var table = table(List.of(
                mix("water", "wart", "awkward"),
                mix("awkward", "rabbit", "leaping"),
                mix("awkward", "sugar", "swiftness"),
                mix("leaping", "eye", "slowness"),
                mix("swiftness", "eye", "slowness")), List.of());
        List<String> slowness = shown(table).stream().filter(line -> line.endsWith("> slowness in potion")).toList();
        assertEquals(List.of("wart+rabbit+eye > slowness in potion", "wart+sugar+eye > slowness in potion"), slowness);
    }

    @Test
    void aLoopIsNeverTakenAndTheChainsAreFinite() {
        var table = table(List.of(
                mix("water", "wart", "a"),
                mix("a", "x", "b"),
                mix("b", "y", "a"),
                mix("b", "z", "water")), List.of());
        List<String> plain = shown(table).stream().filter(line -> line.endsWith(" in potion")).toList();
        assertEquals(List.of("wart > a in potion", "wart+x > b in potion"), plain);
    }

    @Test
    void containerStepsComeOnlyAtTheEndAndEachOnce() {
        var table = table(List.of(mix("water", "wart", "awkward"), mix("awkward", "carrot", "night")), List.of());
        assertEquals(List.of(
                "wart > awkward in potion",
                "wart+gunpowder > awkward in splash",
                "wart+gunpowder+breath > awkward in lingering",
                "wart+carrot > night in potion",
                "wart+carrot+gunpowder > night in splash",
                "wart+carrot+gunpowder+breath > night in lingering",
                "gunpowder > water in splash",
                "gunpowder+breath > water in lingering").stream().sorted().toList(),
                shown(table).stream().sorted().toList());
        for (var chain : BrewingChains.chains(table)) {
            List<String> steps = chain.ingredients();
            int gunpowder = steps.indexOf("gunpowder");
            if (gunpowder >= 0) {
                assertTrue(steps.subList(gunpowder, steps.size()).stream().allMatch(step -> step.equals("gunpowder") || step.equals("breath")),
                        "a container step is followed by a potion step in " + show(chain));
            }
        }
    }

    @Test
    void waterAloneIsNoRecipeButItsContainerSteps() {
        var chains = shown(table(List.of(), List.of()));
        assertEquals(List.of("gunpowder > water in splash", "gunpowder+breath > water in lingering"), chains);
    }

    @Test
    void aStartNoChainFromWaterReachesMakesNone() {
        var table = table(List.of(mix("water", "wart", "awkward"), mix("orphan", "fish", "other")), List.of());
        assertTrue(shown(table).stream().noneMatch(line -> line.contains("fish") || line.contains("other")));
        assertEquals(Set.of("water", "awkward"), BrewingChains.reachable(table));
    }

    @Test
    void aNonPotionResultEndsItsChainWithNoContainerSteps() {
        var table = table(List.of(mix("water", "wart", "awkward")),
                List.of(new ItemMix<>("water", "cane", "resin"), new ItemMix<>("awkward", "cane", "resin")));
        assertEquals(List.of(
                "cane > resin",
                "wart > awkward in potion",
                "wart+gunpowder > awkward in splash",
                "wart+gunpowder+breath > awkward in lingering",
                "wart+cane > resin",
                "gunpowder > water in splash",
                "gunpowder+breath > water in lingering").stream().sorted().toList(),
                shown(table).stream().sorted().toList());
    }

    @Test
    void aBottleIngredientThatAcceptsSeveralPotionsIsOneStepFromEach() {
        // What a mod's brew with a wide bottle ingredient expands to: the same ingredient from every potion it takes.
        var table = table(List.of(mix("water", "wart", "awkward"), mix("water", "dust", "thick")),
                List.of(new ItemMix<>("water", "cane", "resin"), new ItemMix<>("awkward", "cane", "resin"),
                        new ItemMix<>("thick", "cane", "resin")));
        List<String> resin = shown(table).stream().filter(line -> line.endsWith("> resin")).toList();
        assertEquals(List.of("cane > resin", "wart+cane > resin", "dust+cane > resin"), resin);
    }

    @Test
    void theChainsListedAreAllDistinct() {
        var table = table(List.of(mix("water", "wart", "awkward"), mix("awkward", "carrot", "night"),
                mix("night", "dust", "long"), mix("awkward", "dust", "long")), List.of());
        List<String> lines = new ArrayList<>(shown(table));
        assertEquals(lines.size(), Set.copyOf(lines).size());
    }
}
