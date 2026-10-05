// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * Which ghost the Assembler's screen shows where: what an empty slot would take under the Held recipe (the
 * ingredient in the input slot that takes it, the product in its slot) and the product at the head of the
 * screen. The screen draws from this and the recipe viewers ask it what is under the mouse, so the member
 * a tag ingredient shows at a moment and where each ghost stands are worked out once. No client type, so
 * a game test reads it.
 */
public final class AssemblerGhosts {

    /** Where the head's product icon stands, from the panel's corner. */
    public static final int HEAD_X = 8;
    public static final int HEAD_Y = 16;

    /** How long a tag ingredient shows each of its members. */
    public static final long CYCLE_MILLIS = 1000;

    private static final int SIZE = 16;

    private AssemblerGhosts() {
    }

    /** A ghost and where it is drawn, in the screen's coordinates; its count is the ingredient's, or the product's. */
    public record Ghost(ItemStack stack, int x, int y) {
    }

    /**
     * The ghost at ({@code mouseX}, {@code mouseY}) on a panel whose corner is at ({@code left}, {@code top}),
     * or empty over a filled slot, over an empty one with nothing to ghost, with no Held recipe, and over the
     * rest of the panel. {@code millis} is the time that picks a tag ingredient's member.
     */
    public static Optional<Ghost> at(AssemblerMenu menu, int left, int top, int mouseX, int mouseY, long millis) {
        Optional<AssemblerMenu.Held> held = menu.held();
        if (held.isEmpty()) {
            return Optional.empty();
        }
        if (covers(left + HEAD_X, top + HEAD_Y, mouseX, mouseY)) {
            return Optional.of(held.get().product()).filter(stack -> !stack.isEmpty())
                    .map(stack -> new Ghost(stack, left + HEAD_X, top + HEAD_Y));
        }
        for (Slot slot : menu.slots) {
            if (slot.index >= AssemblerSlots.SIZE) {
                break;
            }
            if (covers(left + slot.x, top + slot.y, mouseX, mouseY)) {
                return in(slot, held, millis).map(stack -> new Ghost(stack, left + slot.x, top + slot.y));
            }
        }
        return Optional.empty();
    }

    /** What {@code slot} would take under the Held recipe, or empty when it holds something or takes nothing. */
    private static Optional<ItemStack> in(Slot slot, Optional<AssemblerMenu.Held> held, long millis) {
        if (!slot.getItem().isEmpty()) {
            return Optional.empty();
        }
        ItemStack ghost = ItemStack.EMPTY;
        if (AssemblerSlots.isInput(slot.index)) {
            Optional<SizedIngredient> sized = AssemblerSlots.ingredientFor(slot.index, held.get().ingredients());
            if (sized.isPresent()) {
                List<Holder<Item>> members = sized.get().ingredient().items().toList();
                if (!members.isEmpty()) {
                    int member = (int) (millis / CYCLE_MILLIS % members.size());
                    ghost = new ItemStack(members.get(member), sized.get().count());
                }
            }
        } else if (slot.index == AssemblerSlots.PRODUCT) {
            ghost = held.get().product();
        }
        return ghost.isEmpty() ? Optional.empty() : Optional.of(ghost);
    }

    private static boolean covers(int x, int y, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }
}
