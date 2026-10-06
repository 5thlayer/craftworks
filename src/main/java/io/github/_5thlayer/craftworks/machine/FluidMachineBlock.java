// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A fluid machine's Origin block, the Chemical Plant's or the Oil Refinery's: the footprint's block that holds the
 * block entity and the facing (Groundworks' ADR 0009). Its other positions are
 * {@link io.github._5thlayer.groundworks.FootprintPartBlock}s. Which machine it is comes from the
 * {@link FluidMachines.Entry} it is registered by.
 *
 * <p>The origin draws the whole machine, so it carries what the model needs to draw: {@link #FLUID_CONNECTIONS},
 * which the block entity keeps true while the Held recipe names a fluid, and the model puts a grey ring on the
 * casing at each of the machine's connections. The Assemblers' property, since a ring means the same on both.
 */
public final class FluidMachineBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Whether the Fluid Connections exist, so the casing wears its rings there. */
    public static final BooleanProperty FLUID_CONNECTIONS = AssemblerBlock.FLUID_CONNECTIONS;

    private final FluidMachines.Entry machine;
    private final MapCodec<FluidMachineBlock> codec;

    FluidMachineBlock(Properties properties, FluidMachines.Entry machine) {
        super(properties);
        this.machine = machine;
        this.codec = simpleCodec(props -> new FluidMachineBlock(props, machine));
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(FLUID_CONNECTIONS, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FLUID_CONNECTIONS);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return machine.blockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != machine.blockEntityType().get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((FluidMachineBlockEntity) entity).serverTick((ServerLevel) world);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FluidMachineBlockEntity entity) {
            player.openMenu(entity, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /** Going takes the footprint's parts with it. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        machine.footprint().teardown(level, pos, state.getValue(FACING), pos);
    }
}
