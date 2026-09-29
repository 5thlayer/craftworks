// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jei;

import java.util.stream.Stream;

import com.mojang.blaze3d.platform.InputConstants;
import io.github._5thlayer.craftworks.assembler.FillRequest;
import io.github._5thlayer.craftworks.network.FillRecipePacket;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * JEI's side button on an Assembling recipe: EMI's {@code + Fill Recipe}, pointed at the Personal
 * Assembler (ADR-0004).
 *
 * <p>Left queues one, right five, Shift as many as the inventory covers, middle asks for the Crafting
 * Plan, all through {@link JeiClick} and so {@link FillRequest#of}. Whether the inventory covers the
 * request is the server's call, and a request it can't start opens the Crafting Plan with the reason.
 *
 * <p>JEI's buttons take a left click only, as every vanilla button does; the {@code IconButton} mixin
 * lets this one, and only this one, take the others.
 *
 * <p>The button is always lit, the ingredients present or not: showing what is missing is the Crafting
 * Plan's job. A queueing click leaves JEI's screen open for the next one; the plan replaces it.
 */
public final class AssemblingRecipeButton implements IIconButtonController {

    private final Identifier recipe;

    private AssemblingRecipeButton(Identifier recipe) {
        this.recipe = recipe;
    }

    /** A button for an Assembling recipe's layout, or none for any other. */
    static <T> IIconButtonController forLayout(IRecipeLayoutDrawable<T> layout) {
        if (!layout.getRecipeCategory().getRecipeType().getUid().equals(AssemblingJeiPlugin.ASSEMBLING.getUid())) return null;
        if (!(layout.getRecipe() instanceof RecipeHolder<?> holder && holder.value() instanceof AssemblingRecipe)) {
            return null;
        }
        return new AssemblingRecipeButton(holder.id().identifier());
    }

    @Override
    public void initState(IButtonState state) {
        state.setIcon(new ItemIcon(new ItemStack(Items.CRAFTER)));
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        tooltip.add(Component.translatable("craftworks.fill_recipe"));
        Stream.of("left", "right", "shift", "middle")
                .forEach(key -> tooltip.add(Component.translatable("craftworks.fill_recipe." + key)));
    }

    /**
     * Sends the recipe and what the click asked for. JEI calls this once as the button goes down, to
     * simulate, and again to act; only the second sends.
     */
    @Override
    public boolean onPress(IJeiUserInput input) {
        InputConstants.Key key = input.getKey();
        if (key.getType() != InputConstants.Type.MOUSE) return false;
        if (input.isSimulate()) return true;
        FillRequest request = JeiClick.request(key.getValue(), input.getModifiers());
        ClientPacketDistributor.sendToServer(new FillRecipePacket(recipe, request));
        return true;
    }

    /** Is {@code controller} one of these, for the mixin that lets it take every mouse button. */
    public static boolean is(IIconButtonController controller) {
        return controller instanceof AssemblingRecipeButton;
    }

    /** The crafter, scaled to JEI's small side button. */
    private record ItemIcon(ItemStack stack) implements IDrawable {
        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }

        @Override
        public void draw(GuiGraphicsExtractor graphics, int x, int y) {
            graphics.item(stack, x, y);
        }
    }
}
