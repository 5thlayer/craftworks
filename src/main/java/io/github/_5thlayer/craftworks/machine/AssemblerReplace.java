// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.groundworks.Footprint;
import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Refusal;
import io.github._5thlayer.groundworks.ReplaceBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Fast Replace between the Assembler tiers: a plain click with one tier's item on any block of another
 * tier's footprint swaps it whole, in place, handing back the old item (Groundworks' ADR 0008).
 *
 * <p>The plan is the held tier's footprint at the old one's origin and facing, every position replaced.
 * The block entity is the old origin's, kept across the swap ({@link AssemblerBlock}), so the Held recipe
 * and the contents stay; the old origin's removal hooks drop nothing, since they see a swap.
 */
final class AssemblerReplace implements ReplaceBuilder {

    @Override
    public @Nullable PlacementPlan plan(Level level, @Nullable Player player, ItemStack held, BlockPos aimed, BlockState old) {
        if (!(held.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof AssemblerBlock placing)) {
            return null;
        }
        Footprint standing = Footprint.of(old);
        BlockPos origin = standing == null ? null : standing.standingOrigin(level, aimed, old);
        if (origin == null || !(level.getBlockState(origin).getBlock() instanceof AssemblerBlock at)
                || at.tier() == placing.tier()) {
            return null;
        }
        Direction facing = level.getBlockState(origin).getValue(Footprint.FACING);
        Footprint footprint = Assemblers.footprint(placing.tier());
        List<BlockPos> positions = footprint.positions(origin, facing);
        List<PlacementPlan.Placed> blocks = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            blocks.add(new PlacementPlan.Placed(positions.get(i), footprint.stateAt(i, facing)));
        }
        // The origin lands with the Fluid Connections' rings the old one had, if the new tier has any, so the ring
        // and the capability change together; a tick later the block entity corrects it where the recipe says so.
        BlockState oldOrigin = level.getBlockState(origin);
        BlockState newOrigin = blocks.getFirst().state();
        if (oldOrigin.getValue(AssemblerBlock.FLUID_CONNECTIONS) && placing.tier().hasFluidBoxes()) {
            blocks.set(0, new PlacementPlan.Placed(blocks.getFirst().pos(),
                    newOrigin.setValue(AssemblerBlock.FLUID_CONNECTIONS, true)));
        }
        return PlacementPlan.replacing(blocks, null);
    }

    /** The old tier's item, whichever of its blocks was aimed at. */
    @Override
    public ItemStack refund(Level level, BlockPos aimed, BlockState old) {
        Footprint standing = Footprint.of(old);
        BlockPos origin = standing == null ? null : standing.standingOrigin(level, aimed, old);
        return origin == null ? ItemStack.EMPTY : new ItemStack(level.getBlockState(origin).getBlock().asItem());
    }

    /** The plan has no refusal of its own: it stands on the positions the old footprint did. */
    @Override
    public Component message(Refusal refusal) {
        return Component.translatable("message.groundworks.fast_replace_refused");
    }
}
