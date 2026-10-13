// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.minecraft;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the inventory's 2x2 grid from ever crafting (ADR-0007).
 *
 * <p>The grid has two halves and each fails independently. <b>The slots go</b> in
 * {@link AbstractCraftingMenuMixin}, which swaps them for {@code RemovedGridSlot} as the inventory menu
 * adds them, with every index kept: {@code quickMoveStack} names the armour and main inventory ranges by
 * literal, the network protocol addresses slots by index, and other mods read them.
 *
 * <p><b>The recipe never resolves.</b> {@code slotsChanged} is the only thing that fills the result
 * container, so cancelling it means no crafting can happen even where something puts items into the grid
 * without going through a slot -- the recipe book's {@code ServerPlaceRecipe} writes to the container
 * directly, and a mod could too. This half is the inventory menu's alone: a copy's grid crafts nothing
 * anyway, since crafting recipes are converted away at load (ADR-0011).
 *
 * <p>The grid is also painted on the inventory texture, which no menu change reaches; the cover and
 * the recipe book button are {@code InventoryGridBlank}'s, client-side. Neither is load-bearing --
 * the mixins are what make the grid unable to craft.
 */
@Mixin(InventoryMenu.class)
public class InventoryMenuMixin {

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void craftworks$neverCraft(Container container, CallbackInfo ci) {
        ci.cancel();
    }
}
