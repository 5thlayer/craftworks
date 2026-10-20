// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.ArrayList;
import java.util.List;

/**
 * A generated Assembling recipe set shaped like the worst of a big pack (#67): deep chains whose every
 * ingredient accepts many items, each made several ways, with nothing at the bottom held.
 *
 * <p>Two parts. The <b>layers</b>: level 0 is raw (no recipe makes it), and every item above it has
 * {@code routes} recipes, each wanting one of <em>any</em> item of the level below plus one specific one.
 * A search that fails costs {@code (width * routes)} to the power of the depth, which is the product the
 * Resolver's search limit exists for. The <b>banners</b>: sixteen colours of banner, each wanting six of
 * any of sixteen wools, each wool made from its own dye, each dye from its own flower or bone, and a stick.
 */
final class BigRecipes {

    static final int COLOURS = 16;

    private BigRecipes() {
    }

    static String raw(int index) {
        return "raw_" + index;
    }

    static String layer(int level, int index) {
        return "l" + level + "_" + index;
    }

    static AssemblingRecipe recipe(String id, String result, Ingredient... ingredients) {
        return new AssemblingRecipe(id, List.of(ingredients), new ItemAmount(result, 1), 10, 0, true);
    }

    /** Layers {@code 1..depth} over {@code width} raw items, plus the banners when asked. */
    static AssemblingRecipeSet build(int depth, int width, int routes, boolean banners) {
        AssemblingRecipeSet.Builder builder = AssemblingRecipeSet.builder();
        for (int level = 1; level <= depth; level++) {
            for (int i = 0; i < width; i++) {
                for (int r = 0; r < routes; r++) {
                    List<String> below = new ArrayList<>();
                    for (int k = 0; k < width; k++) {
                        int j = (k + i + r) % width;
                        below.add(level == 1 ? raw(j) : layer(level - 1, j));
                    }
                    builder.add(recipe("r_" + level + "_" + i + "_" + r, layer(level, i),
                            new Ingredient(below, 1), Ingredient.of(raw((i + r) % width), 1)));
                }
            }
        }
        if (banners) addBanners(builder);
        return builder.build();
    }

    private static void addBanners(AssemblingRecipeSet.Builder builder) {
        List<String> wools = new ArrayList<>();
        for (int c = 0; c < COLOURS; c++) wools.add("wool_" + c);
        builder.add(recipe("stick", "stick", Ingredient.of("plank", 2)));
        for (int c = 0; c < COLOURS; c++) {
            builder.add(recipe("dye_a_" + c, "dye_" + c, Ingredient.of("flower_" + c, 1)));
            builder.add(recipe("dye_b_" + c, "dye_" + c, Ingredient.of("bone", 1), Ingredient.of("pigment_" + c, 1)));
            builder.add(recipe("wool_" + c, "wool_" + c, Ingredient.of("dye_" + c, 1), Ingredient.of("string", 4)));
            builder.add(recipe("banner_" + c, "banner_" + c, new Ingredient(wools, 6), Ingredient.of("stick", 1)));
        }
    }

    /** An inventory of {@code filler} unrelated stacks, plus a few of the raw items of the first chains. */
    static ItemBag stock(int filler, int... heldRaw) {
        ItemBag bag = new ItemBag();
        for (int i = 0; i < filler; i++) bag.add("filler_" + i, 64);
        for (int i : heldRaw) bag.add(raw(i), 4);
        return bag;
    }
}
