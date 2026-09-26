// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

/**
 * A count of one item, named by its {@link ItemKey}: its registry id together with its data
 * component patch, as a string.
 *
 * <p>A string and not an {@code ItemStack} on purpose: everything the planner does with an item is
 * counting it, and keeping the identity a string is what lets the Resolver and the Assembler queue be
 * unit tests rather than world loads.
 */
public record ItemAmount(String item, int count) {

    public ItemAmount {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("an item amount needs an item key");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("an item amount of " + count + " is not an amount");
        }
    }
}
