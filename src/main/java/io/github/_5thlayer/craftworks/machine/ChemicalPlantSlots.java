// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * A Chemical Plant's item slots: two inputs, which take the Held recipe's first and second ingredient, and the
 * product's. It has no remainder slot: a recipe that would leave one is refused at Fill Recipe.
 */
public final class ChemicalPlantSlots {

    public static final int INPUTS = 2;

    public static final int PRODUCT = INPUTS;

    public static final int SIZE = INPUTS + 1;

    private ChemicalPlantSlots() {
    }
}
