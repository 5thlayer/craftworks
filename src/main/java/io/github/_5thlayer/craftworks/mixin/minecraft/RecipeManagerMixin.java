// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.minecraft;

import io.github._5thlayer.craftworks.recipe.BrewedRecipes;
import io.github._5thlayer.craftworks.recipe.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Converts mods' crafting recipes (ADR-0011) and adds the Brewed recipes (ADR-0026) as the recipes load: once every datapack has been read and
 * KubeJS, which edits them while they are read, is done, and as the loaded map is handed on.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow
    @Final
    private HolderLookup.Provider registries;

    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/world/item/crafting/RecipeMap;",
            at = @At("RETURN"), cancellable = true)
    private void craftworks$convertModRecipes(ResourceManager manager, ProfilerFiller profiler,
            CallbackInfoReturnable<RecipeMap> cir) {
        cir.setReturnValue(BrewedRecipes.add(ModRecipes.convert(cir.getReturnValue(), registries), registries));
    }
}
