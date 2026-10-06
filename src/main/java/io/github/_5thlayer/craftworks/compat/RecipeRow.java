// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat;

/**
 * How wide an Assembling recipe is in a recipe viewer, EMI or JEI: the row of slots, or the widest line of
 * text under it if that is wider, so the category and the seconds always sit inside the recipe. The text
 * widths are the viewer's own to measure; this holds the rule, which needs no client.
 */
public final class RecipeRow {

    public static final int SLOT = 18;
    /** The gap between the ingredients and the results, where the arrow is drawn. */
    public static final int ARROW = 30;
    /** The seconds line starts this far past the ingredients, beside the arrow. */
    public static final int SECONDS_INSET = 3;

    private RecipeRow() {
    }

    /**
     * The recipe's width. {@code inputs} counts every ingredient slot, fluid ones too, and {@code outputs}
     * every result slot; the seconds line is drawn under the arrow and the category line from the left.
     */
    public static int width(int inputs, int outputs, int secondsTextWidth, int categoryTextWidth) {
        int slots = inputs * SLOT + ARROW + outputs * SLOT;
        int seconds = inputs * SLOT + SECONDS_INSET + secondsTextWidth;
        return Math.max(slots, Math.max(seconds, categoryTextWidth));
    }
}
