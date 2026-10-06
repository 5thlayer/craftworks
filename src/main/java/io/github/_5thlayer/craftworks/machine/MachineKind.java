// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * Which machine that makes a Held recipe a message is about: an Assembler of any tier, or the Chemical Plant.
 * Each has its own lang keys under its name, such as {@code craftworks.chemical_plant.refused.locked}. Pure: no
 * Minecraft types.
 */
public enum MachineKind {
    ASSEMBLER("assembler"),
    CHEMICAL_PLANT("chemical_plant");

    private final String langName;

    MachineKind(String langName) {
        this.langName = langName;
    }

    /** The machine's lang key ending in {@code suffix}, say {@code craftworks.assembler.fill_recipe}. */
    public String langKey(String suffix) {
        return "craftworks." + langName + "." + suffix;
    }
}
