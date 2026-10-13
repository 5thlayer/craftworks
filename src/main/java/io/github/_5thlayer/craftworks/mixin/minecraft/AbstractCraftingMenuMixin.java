// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.minecraft;

import io.github._5thlayer.craftworks.assembler.InventoryGrid;
import io.github._5thlayer.craftworks.assembler.RemovedGridSlot;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Removes the 2x2 crafting grid from every menu that has one (ADR-0007): the inventory's and any copy of it.
 *
 * <p>A 2x2 {@code AbstractCraftingMenu} builds its grid through {@code addResultSlot} and
 * {@code addCraftingGridSlots}, so those are where the live slots are swapped for {@link RemovedGridSlot},
 * before they are added. Swapping at the source rather than after the menu is built matters because a
 * copy can rebuild its slots whenever it likes -- clear them and call these two again -- and a swap made
 * once, when the menu opened, would leave live slots under the cover after the first rebuild. It also
 * covers the client's menu and the server's alike, with nothing to register. The slot goes in at the
 * index it would have had, so every index stays constant. The 3x3 crafting table is left as it is.
 */
@Mixin(AbstractCraftingMenu.class)
public abstract class AbstractCraftingMenuMixin {

    private static final String ADD_SLOT =
            "Lnet/minecraft/world/inventory/AbstractCraftingMenu;addSlot(Lnet/minecraft/world/inventory/Slot;)Lnet/minecraft/world/inventory/Slot;";

    @ModifyArg(method = "addResultSlot", at = @At(value = "INVOKE", target = ADD_SLOT))
    private Slot craftworks$removeResultSlot(Slot slot) {
        return craftworks$removeIf2x2(slot);
    }

    @ModifyArg(method = "addCraftingGridSlots", at = @At(value = "INVOKE", target = ADD_SLOT))
    private Slot craftworks$removeGridSlot(Slot slot) {
        return craftworks$removeIf2x2(slot);
    }

    private Slot craftworks$removeIf2x2(Slot slot) {
        return InventoryGrid.is2x2((AbstractCraftingMenu) (Object) this) ? RemovedGridSlot.standingIn(slot) : slot;
    }
}
