// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.function.Supplier;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.groundworks.Footprint;
import io.github._5thlayer.groundworks.FootprintItem;
import io.github._5thlayer.groundworks.FootprintPartBlock;
import io.github._5thlayer.groundworks.FootprintShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * The Oil Refinery's registrations (#27): a block and an item standing on a Groundworks footprint of their
 * own, a block entity type, and the menu. It keeps its Held recipe on the item in the Assemblers' component.
 *
 * <p>The footprint is 5x5x3, Factorio's tile square three blocks tall, the origin at its bottom centre. The
 * refinery ships no recipes of its own; a Consumer supplies the {@code oil-processing} ones.
 */
public final class OilRefineries {

    /** The refinery's footprint shape: 5x5 on the ground and three blocks tall, 74 parts. */
    private static final FootprintShape SHAPE = FootprintShape.square(5, 3);

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Craftworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Craftworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Craftworks.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Craftworks.MOD_ID);

    public static final DeferredBlock<OilRefineryBlock> BLOCK = BLOCKS.registerBlock("oil_refinery",
            properties -> new OilRefineryBlock(Assemblers.machine(properties)));

    private static final DeferredBlock<FootprintPartBlock> PART = BLOCKS.registerBlock("oil_refinery_part",
            properties -> new FootprintPartBlock(Assemblers.machine(properties).noLootTable(), OilRefineries::footprint));

    public static final DeferredItem<FootprintItem> ITEM = ITEMS.registerItem("oil_refinery",
            properties -> new FootprintItem(footprint(), properties.useBlockDescriptionPrefix()));

    private static final Footprint FOOTPRINT = Footprint.declare(SHAPE, BLOCK, PART, ITEM);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("oil_refinery", () -> new BlockEntityType<>(OilRefineries::blockEntity, BLOCK.get()));

    public static final Supplier<MenuType<FluidMachineMenu>> MENU =
            MENUS.register("oil_refinery", () -> IMenuTypeExtension.create((containerId, playerInventory, buffer) ->
                    new FluidMachineMenu(OilRefineries.MENU.get(), containerId, playerInventory, buffer, FluidMachine.OIL_REFINERY)));

    private OilRefineries() {
    }

    /** An Oil Refinery's block entity: the fluid machine, as {@link FluidMachine#OIL_REFINERY} describes it. */
    public static FluidMachineBlockEntity blockEntity(BlockPos pos, BlockState state) {
        return new FluidMachineBlockEntity(BLOCK_ENTITY.get(), pos, state, FluidMachine.OIL_REFINERY, MENU);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(OilRefineries::registerCapabilities);
    }

    /** The refinery's footprint. */
    public static Footprint footprint() {
        return FOOTPRINT;
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // On every face of every block of the footprint: Groundworks' parts forward their lookups here. No item
        // capability: the refinery has no item slots to show.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BLOCK_ENTITY.get(), (machine, side) -> machine.energyFace());
        // Fluid is not forwarded whole as those are: only the five connection blocks answer, on their one outward
        // face, and a part's lookup sees no more than its position. The origin has none, so Groundworks' forward
        // finds nothing there and falls through to this one.
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, entity, side) -> fluidConnection(level, pos, state, side),
                PART.get());
    }

    /** What a part block answers to a fluid lookup: its refinery's boxes if the block is a Fluid Connection and the face is its own. */
    private static @Nullable ResourceHandler<FluidResource> fluidConnection(Level level, BlockPos pos, BlockState state,
            @Nullable Direction side) {
        if (side == null || !(state.getBlock() instanceof FootprintPartBlock part)) {
            return null;
        }
        BlockPos origin = part.footprint().standingOrigin(level, pos, state);
        return origin != null && level.getBlockEntity(origin) instanceof FluidMachineBlockEntity machine
                ? machine.fluidConnection(pos, side) : null;
    }
}
