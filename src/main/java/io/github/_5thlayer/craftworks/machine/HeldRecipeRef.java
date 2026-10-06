// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;
import java.util.function.Predicate;

import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

/**
 * A machine's Held recipe: an id, resolved when asked and never on load, when the recipes may not be there.
 *
 * <p>Asked every tick and by every pipe, so what is worked out once per recipe instance is cached: whether
 * the machine could run it at all, which a reload of the recipes hands back new instances to ask again.
 */
final class HeldRecipeRef {

    private @Nullable Identifier id;
    /** The recipe instance {@link #runnable} last checked, and whether the machine can run it. Never saved. */
    private @Nullable AssemblingRecipe checked;
    private boolean checkedRuns;

    Optional<Identifier> id() {
        return Optional.ofNullable(id);
    }

    @Nullable Identifier idOrNull() {
        return id;
    }

    void set(@Nullable Identifier id) {
        this.id = id;
    }

    /**
     * The Held recipe if the machine can run it: {@code canRun} is asked once per recipe instance, {@code takes}
     * every time, since a Fast Replace or a config edit moves what it asks.
     */
    Optional<AssemblingRecipe> runnable(ServerLevel server, Predicate<AssemblingRecipe> canRun,
            Predicate<AssemblingRecipe> takes) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<AssemblingRecipe> recipe = HeldRecipes.find(server, id).map(RecipeHolder::value);
        recipe.filter(found -> found != checked).ifPresent(found -> {
            checked = found;
            checkedRuns = canRun.test(found);
        });
        return recipe.filter(found -> checkedRuns && takes.test(found));
    }
}
