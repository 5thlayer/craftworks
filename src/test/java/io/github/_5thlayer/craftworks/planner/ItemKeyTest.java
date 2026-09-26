// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item identity itself (ADR-0002): an item is its registry id together with its data component
 * patch, encoded as one string.
 *
 * <p>What a component's value encodes to is vanilla's business and this never asks; what it asserts
 * is the framing and the ordering, which is where the two ways to get the identity wrong both live:
 * folding two items onto one key, and splitting one item across two.
 */
class ItemKeyTest {

    private static final String POTION = "minecraft:potion";
    private static final String CONTENTS = "minecraft:potion_contents";

    private static String potionKey(String potion) {
        return ItemKey.of(POTION, List.of(ItemKey.Entry.set(CONTENTS, "{potion:\"minecraft:" + potion + "\"}")));
    }

    @Test
    void anItemWithNoComponentsIsExactlyItsRegistryId() {
        assertEquals("minecraft:iron_ingot", ItemKey.of("minecraft:iron_ingot", List.of()));
        assertEquals("minecraft:iron_ingot", ItemKey.of("minecraft:iron_ingot", null));
        assertFalse(ItemKey.hasPatch("minecraft:iron_ingot"));
    }

    @Test
    void aComponentBearingItemNamesItsComponentInVanillasOwnSyntax() {
        assertEquals(
                "minecraft:potion[minecraft:potion_contents={potion:\"minecraft:swiftness\"}]",
                potionKey("swiftness"));
        assertTrue(ItemKey.hasPatch(potionKey("swiftness")));
    }

    @Test
    void twoPotionsAreTwoItems() {
        // Under a bare-id identity these are one string, and a plan for one potion would be
        // satisfiable out of a stack of the other.
        assertNotEquals(potionKey("swiftness"), potionKey("healing"));
        assertEquals(POTION, ItemKey.itemId(potionKey("swiftness")));
        assertEquals(POTION, ItemKey.itemId(potionKey("healing")));
    }

    @Test
    void aBarePotionIsNotAnyOfThem() {
        // Matching is exact, a deliberate divergence from neoforge:components' subset match: a key
        // with no patch names the pristine item and nothing else.
        assertNotEquals(POTION, potionKey("swiftness"));
    }

    @Test
    void twoDifferentlyOrderedPatchesEncodeIdentically() {
        // DataComponentPatch guarantees no iteration order across loads. Skipping the sort would work
        // for every one-component item and then produce two keys for the first two-component one.
        List<ItemKey.Entry> one = List.of(
                ItemKey.Entry.set(CONTENTS, "{potion:\"minecraft:swiftness\"}"),
                ItemKey.Entry.set("minecraft:custom_name", "'{\"text\":\"b\"}'"));
        List<ItemKey.Entry> other = List.of(one.get(1), one.get(0));

        assertEquals(ItemKey.of(POTION, one), ItemKey.of(POTION, other));
        assertEquals(
                POTION + "[minecraft:custom_name='{\"text\":\"b\"}',minecraft:potion_contents={potion:\"minecraft:swiftness\"}]",
                ItemKey.of(POTION, one));
    }

    @Test
    void aRemovedComponentIsNamedTheWayVanillaNamesOne() {
        assertEquals(POTION + "[!minecraft:potion_contents]", ItemKey.of(POTION, List.of(ItemKey.Entry.removed(CONTENTS))));
    }

    @Test
    void theItemIdIsReadableBackOutOfAnyKey() {
        assertEquals("minecraft:iron_ingot", ItemKey.itemId("minecraft:iron_ingot"));
        assertEquals(POTION, ItemKey.itemId(POTION + "[!minecraft:potion_contents]"));
    }
}
