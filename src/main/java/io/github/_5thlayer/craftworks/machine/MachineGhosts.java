// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;
import java.util.function.IntSupplier;

import io.github._5thlayer.libworks.ghost.Ghost;
import io.github._5thlayer.libworks.ghost.Ghosts;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * What a machine's screen ghosts where: the positions of an Assembler's menu registered on a libworks
 * {@link Ghosts}, each asking the Held recipe what it takes there (the ingredient in the input slot that takes
 * it, the product in its slot) and the product at the head of the screen. The Held recipe is read each time
 * a ghost is asked for, so the ghosts change with it. The cycling of a tag ingredient and the hit test are
 * the helper's. No client type, so a game test reads it. Where a machine's own slots are is its menu's
 * {@link MachineSlots}.
 */
public final class MachineGhosts {

    /** Where the head's product icon stands, from the panel's corner. */
    public static final int HEAD_X = 8;
    public static final int HEAD_Y = 16;

    private MachineGhosts() {
    }

    /**
     * The ghosts of {@code menu}'s screen, whose panel's corner is ({@code left}, {@code top}), read each
     * time so they follow a resize. Empty slots with nothing to ghost, filled ones and every position with no
     * Held recipe have none.
     */
    public static Ghosts of(AssemblerMenu menu, IntSupplier left, IntSupplier top) {
        Ghosts ghosts = new Ghosts(left, top);
        MachineSlots layout = menu.layout();
        for (Slot slot : menu.slots) {
            if (slot.index >= layout.size()) {
                break;
            }
            if (layout.isInput(slot.index)) {
                ghosts.add(slot, () -> menu.held()
                        .flatMap(held -> layout.ingredientFor(slot.index, held.ingredients()))
                        .map(Ghosts::of).orElse(List.of()));
            } else if (slot.index == layout.product()) {
                ghosts.add(slot, () -> product(menu));
            }
        }
        ghosts.add(HEAD_X, HEAD_Y, () -> product(menu));
        return ghosts;
    }

    /** Whether {@code ghost} is the head's icon, which the screen draws as the item itself, not veiled. */
    public static boolean isHead(Ghost ghost, int left, int top) {
        return ghost.x() == left + HEAD_X && ghost.y() == top + HEAD_Y;
    }

    private static List<ItemStack> product(AssemblerMenu menu) {
        return menu.held().map(HeldRecipeView::product).filter(stack -> !stack.isEmpty()).map(List::of).orElse(List.of());
    }
}
