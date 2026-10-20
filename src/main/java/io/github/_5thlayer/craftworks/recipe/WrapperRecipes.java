// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * Wrapper recipes (ADR-0023, #68): crafting recipes that are neither shaped nor shapeless and hand out the
 * one they wrap through an interface named {@code IWrapperRecipe}, as Sophisticated Backpacks' do. Matched by
 * name and read by reflection, so the Mod compiles against no other mod.
 */
final class WrapperRecipes {

    private WrapperRecipes() {
    }

    /**
     * The shaped or shapeless recipe {@code recipe} wraps, or null when it is not a wrapper: it implements an
     * interface named {@code IWrapperRecipe}, has a public no-arg {@code getCompose()} and that returns a
     * shaped or shapeless recipe. Any failure to ask reads as not a wrapper.
     */
    static Recipe<?> unwrap(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe || !implementsWrapper(recipe.getClass())) {
            return null;
        }
        try {
            for (Method method : recipe.getClass().getMethods()) {
                if (!method.getName().equals("getCompose") || method.getParameterCount() != 0 || method.isBridge()
                        || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Object inner = method.invoke(recipe);
                return inner instanceof ShapedRecipe || inner instanceof ShapelessRecipe ? (Recipe<?>) inner : null;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Not a wrapper we can read.
        }
        return null;
    }

    private static boolean implementsWrapper(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> implemented : c.getInterfaces()) {
                if (implemented.getSimpleName().equals("IWrapperRecipe") || implementsWrapper(implemented)) return true;
            }
        }
        return false;
    }
}
