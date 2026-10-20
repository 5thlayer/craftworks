// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import net.minecraft.world.item.crafting.Recipe;

/**
 * Stands in for Sophisticated Core's interface of the same name (#68), which the Mod matches by its simple
 * name: a recipe that hands out the vanilla recipe it wraps.
 */
public interface IWrapperRecipe<T extends Recipe<?>> {

    T getCompose();
}
