// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

/**
 * The Creative Fluid Source: a creative-only block that gives fluid to whatever pulls from it, so an
 * Assembler's Fluid Connections work where no fluid mod is installed (#32). A filled bucket used on it sets
 * its fluid and is not emptied; an empty bucket clears it. It has no recipe and appears only in the
 * creative tab.
 */
public final class CreativeFluidSourceBlock extends BaseEntityBlock {

    public static final MapCodec<CreativeFluidSourceBlock> CODEC = simpleCodec(CreativeFluidSourceBlock::new);

    public CreativeFluidSourceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<CreativeFluidSourceBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeFluidSourceBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        FluidStack held = FluidUtil.getFirstStackContained(stack);
        if (held.isEmpty() && !stack.is(Items.BUCKET)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CreativeFluidSourceBlockEntity source) {
            source.setFluid(held.isEmpty() ? FluidResource.EMPTY : FluidResource.of(held));
        }
        return InteractionResult.SUCCESS;
    }
}
