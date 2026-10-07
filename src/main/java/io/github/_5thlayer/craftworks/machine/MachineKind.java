// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * Which machine that makes a Held recipe: an Assembler of any tier. It names the machine's lang keys, such as
 * {@code craftworks.assembler.refused.locked}, and its Recipe viewer tab
 * ({@link MachineTabs}). Pure: no Minecraft types.
 */
public enum MachineKind {
    ASSEMBLER("assembler");

    private final String langName;

    MachineKind(String langName) {
        this.langName = langName;
    }

    /** The machine's Recipe viewer tab, {@code craftworks:} this: {@code assembler}. */
    public String tabName() {
        return langName;
    }

    /** The machine's lang key ending in {@code suffix}, say {@code craftworks.assembler.fill_recipe}. */
    public String langKey(String suffix) {
        return "craftworks." + langName + "." + suffix;
    }
}
