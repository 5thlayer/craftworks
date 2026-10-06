// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * Which machine that makes a Held recipe: an Assembler of any tier, the Chemical Plant or the Oil Refinery. It names the
 * machine's lang keys, such as {@code craftworks.chemical_plant.refused.locked}, and its Recipe viewer tab
 * ({@link MachineTabs}). Pure: no Minecraft types.
 */
public enum MachineKind {
    ASSEMBLER("assembler"),
    CHEMICAL_PLANT("chemical_plant"),
    OIL_REFINERY("oil_refinery");

    private final String langName;

    MachineKind(String langName) {
        this.langName = langName;
    }

    /** The machine's Recipe viewer tab, {@code craftworks:} this: {@code assembler}, {@code chemical_plant} or {@code oil_refinery}. */
    public String tabName() {
        return langName;
    }

    /** The machine's lang key ending in {@code suffix}, say {@code craftworks.assembler.fill_recipe}. */
    public String langKey(String suffix) {
        return "craftworks." + langName + "." + suffix;
    }
}
