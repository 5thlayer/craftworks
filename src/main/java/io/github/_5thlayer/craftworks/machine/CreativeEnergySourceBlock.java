// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Creative Energy Source: a creative-only block that powers whatever touches it, so an Assembler runs
 * where no energy mod is installed (#23). It has no recipe and appears only in the creative tab.
 */
public final class CreativeEnergySourceBlock extends BaseEntityBlock {

    public static final MapCodec<CreativeEnergySourceBlock> CODEC = simpleCodec(CreativeEnergySourceBlock::new);

    public CreativeEnergySourceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<CreativeEnergySourceBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeEnergySourceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != Assemblers.CREATIVE_ENERGY_SOURCE_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((CreativeEnergySourceBlockEntity) entity).serverTick((ServerLevel) world);
    }
}
