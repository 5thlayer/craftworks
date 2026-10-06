// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A plain fluid-handler block for the game tests: it holds one fluid in a tank and exposes it on every face
 * through NeoForge's fluid capability, as a tank or a pipe of another mod would. Registered only when the
 * game tests run ({@code craftworks.gametestPack}), so no player's world has the block.
 */
final class TestTank {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Craftworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Craftworks.MOD_ID);

    static final DeferredBlock<Block> BLOCK = BLOCKS.registerBlock("gametest_tank", Block::new);

    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Entity>> ENTITY =
            BLOCK_ENTITIES.register("gametest_tank", () -> new BlockEntityType<>(Entity::new, BLOCK.get()));

    private TestTank() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(TestTank::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ENTITY.get(), (tank, side) -> tank.face);
    }

    static final class Block extends BaseEntityBlock {

        private static final MapCodec<Block> CODEC = simpleCodec(Block::new);

        Block(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<Block> codec() {
            return CODEC;
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Entity(pos, state);
        }
    }

    /** What a tank lets through its faces. */
    enum Mode {
        /** Anything inserts and extracts, as a tank of another mod's does. */
        BOTH,
        /** Only extracts: a source, which a machine's outputs have no way into. */
        SOURCE,
        /** Only inserts: a drain, which a machine's inputs have nothing to pull from. */
        SINK
    }

    /** The tank: 8,000 mB of one fluid, which anything may insert and extract unless its {@link Mode} says otherwise. */
    static final class Entity extends BlockEntity {

        final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 8000);

        Mode mode = Mode.BOTH;

        /** What the capability shows: the tank, as far as the mode lets anything through. */
        final ResourceHandler<FluidResource> face = new ResourceHandler<>() {
            @Override
            public int size() {
                return tank.size();
            }

            @Override
            public FluidResource getResource(int index) {
                return tank.getResource(index);
            }

            @Override
            public long getAmountAsLong(int index) {
                return tank.getAmountAsLong(index);
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return tank.getCapacityAsLong(index, resource);
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return tank.isValid(index, resource);
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return mode == Mode.SOURCE ? 0 : tank.insert(index, resource, amount, transaction);
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return mode == Mode.SINK ? 0 : tank.extract(index, resource, amount, transaction);
            }
        };

        Entity(BlockPos pos, BlockState state) {
            super(ENTITY.get(), pos, state);
        }

        void fill(Fluid fluid, int amount) {
            tank.set(0, FluidResource.of(fluid), amount);
        }
    }
}
