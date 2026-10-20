// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * A crafting recipe that wraps a {@link ShapedRecipe} without extending it, as Sophisticated Backpacks' tier
 * upgrades do (#68): it says {@code isSpecial()}, and its result takes the custom name and damage of the first
 * input that has a name. It is only made from a grid laid out as the wrapped pattern lays it, so a craft that
 * lays the stacks out any other way gets nothing. It stands in for another mod's, so its serializer is
 * registered only by the Mod's own runs.
 */
@EventBusSubscriber(modid = Craftworks.MOD_ID)
public final class WrapsShapedRecipe implements CraftingRecipe, IWrapperRecipe<ShapedRecipe> {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "gametest_wraps_shaped");

    public static final RecipeSerializer<WrapsShapedRecipe> SERIALIZER = new RecipeSerializer<>(
            ShapedRecipe.MAP_CODEC.xmap(WrapsShapedRecipe::new, WrapsShapedRecipe::getCompose),
            ShapedRecipe.STREAM_CODEC.map(WrapsShapedRecipe::new, WrapsShapedRecipe::getCompose));

    /** How many times {@link #assemble} has been asked, for the test that counts the machine's asks. */
    static final AtomicInteger ASSEMBLES = new AtomicInteger();

    private final ShapedRecipe inner;

    WrapsShapedRecipe(ShapedRecipe inner) {
        this.inner = inner;
    }

    @Override
    public ShapedRecipe getCompose() {
        return inner;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return inner.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ASSEMBLES.incrementAndGet();
        // The wrapped recipe's grid, 1 wide and 2 high: a diamond over a sword.
        if (input.width() != inner.getWidth() || input.height() != inner.getHeight()
                || !input.getItem(0).is(Items.DIAMOND) || !input.getItem(1).is(Items.IRON_SWORD)) {
            return ItemStack.EMPTY;
        }
        ItemStack made = inner.assemble(input);
        for (ItemStack stack : input.items()) {
            if (!stack.has(DataComponents.CUSTOM_NAME)) continue;
            made.set(DataComponents.CUSTOM_NAME, stack.get(DataComponents.CUSTOM_NAME));
            made.setDamageValue(stack.getDamageValue());
            break;
        }
        return made;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return inner.showNotification();
    }

    @Override
    public String group() {
        return inner.group();
    }

    @Override
    public CraftingBookCategory category() {
        return inner.category();
    }

    @Override
    public PlacementInfo placementInfo() {
        return inner.placementInfo();
    }

    @Override
    public RecipeSerializer<WrapsShapedRecipe> getSerializer() {
        return SERIALIZER;
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_SERIALIZER, ID, () -> SERIALIZER);
    }
}
