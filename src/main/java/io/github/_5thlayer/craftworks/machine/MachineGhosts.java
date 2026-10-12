// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import io.github._5thlayer.libworks.ghost.Ghost;
import io.github._5thlayer.libworks.ghost.Ghosts;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/**
 * What a machine's screen ghosts where: the positions of an Assembler's menu registered on a libworks
 * {@link Ghosts}, each asking the Held recipe what it takes there (the ingredient in the input slot that takes
 * it, the product in its slot), the fluid containers of the drain and fill slots, and the product at the head of
 * the screen. The Held recipe is read each time
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
     * The ghosts of one Assembler screen, apart by registration: {@code slots} are drawn veiled, and {@code head}
     * only answers the hit test, since the screen draws the head's product as the item itself.
     */
    public record Screen(Ghosts slots, Ghosts head) {

        /** The ghost at ({@code mouseX}, {@code mouseY}) at {@code millis}, the head's first, or empty. */
        public Optional<Ghost> at(double mouseX, double mouseY, long millis) {
            Optional<Ghost> over = head.at(mouseX, mouseY, millis);
            return over.isPresent() ? over : slots.at(mouseX, mouseY, millis);
        }
    }

    /**
     * The ghosts of {@code menu}'s screen, whose panel's corner is ({@code left}, {@code top}), read each
     * time so they follow a resize. Empty slots with nothing to ghost, filled ones and every position with no
     * Held recipe have none.
     */
    public static Screen forAssembler(AssemblerMenu menu, IntSupplier left, IntSupplier top) {
        Ghosts slots = new Ghosts(left, top);
        MachineSlots layout = menu.layout();
        for (Slot slot : menu.slots) {
            if (slot.index >= layout.size()) {
                break;
            }
            if (layout.isInput(slot.index)) {
                slots.add(slot, () -> menu.held()
                        .flatMap(held -> layout.ingredientFor(slot.index, held.ingredients()))
                        .map(Ghosts::of).orElse(List.of()));
            } else if (slot.index == layout.firstOutput()) {
                slots.add(slot, () -> product(menu));
            } else if (slot.index == AssemblerSlots.DRAIN) {
                slots.add(slot, drainCandidates(menu));
            } else if (slot.index == AssemblerSlots.FILL) {
                slots.add(slot, AssemblerContainers::emptyContainers);
            }
        }
        Ghosts head = new Ghosts(left, top);
        head.add(HEAD_X, HEAD_Y, () -> product(menu));
        return new Screen(slots, head);
    }

    /**
     * The drain slot's candidates: each fluid the Held recipe's fluid ingredients take, as a filled container of
     * each empty one ({@code empties}), a fluid with no container in the loaded mods skipped. Worked out again only
     * when the Held recipe changes.
     */
    private static Supplier<List<ItemStack>> drainCandidates(AssemblerMenu menu) {
        return new Supplier<>() {
            private @Nullable HeldRecipeView forRecipe;
            private List<ItemStack> candidates = List.of();

            @Override
            public List<ItemStack> get() {
                HeldRecipeView held = menu.held().orElse(null);
                if (held == null) {
                    return List.of();
                }
                if (held != forRecipe) {
                    forRecipe = held;
                    candidates = AssemblerContainers.filledForms(AssemblerContainers.emptyContainers(), fluidsOf(held));
                }
                return candidates;
            }
        };
    }

    private static List<Fluid> fluidsOf(HeldRecipeView held) {
        return held.fluidIngredients().stream().flatMap(sized -> sized.ingredient().fluids().stream()).map(Holder::value).distinct().toList();
    }

    private static List<ItemStack> product(AssemblerMenu menu) {
        return menu.held().map(HeldRecipeView::product).filter(stack -> !stack.isEmpty()).map(List::of).orElse(List.of());
    }
}
