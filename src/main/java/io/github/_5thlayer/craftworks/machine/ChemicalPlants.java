// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.function.Supplier;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.groundworks.Footprint;
import io.github._5thlayer.groundworks.FootprintItem;
import io.github._5thlayer.groundworks.FootprintPartBlock;
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
 * The Chemical Plant's registrations (#26): a block and an item standing on a Groundworks footprint of their
 * own, a block entity type, and the menu. It keeps its Held recipe on the item in the Assemblers' component.
 *
 * <p>The footprint is the Assemblers': 3x2x3, Factorio's tile square two blocks tall, the origin at its
 * bottom centre. The plant ships no recipes of its own; a Consumer supplies the {@code chemistry} ones.
 */
public final class ChemicalPlants {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Craftworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Craftworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Craftworks.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Craftworks.MOD_ID);

    public static final DeferredBlock<ChemicalPlantBlock> BLOCK = BLOCKS.registerBlock("chemical_plant",
            properties -> new ChemicalPlantBlock(Assemblers.machine(properties)));

    private static final DeferredBlock<FootprintPartBlock> PART = BLOCKS.registerBlock("chemical_plant_part",
            properties -> new FootprintPartBlock(Assemblers.machine(properties).noLootTable(), ChemicalPlants::footprint));

    public static final DeferredItem<FootprintItem> ITEM = ITEMS.registerItem("chemical_plant",
            properties -> new FootprintItem(footprint(), properties.useBlockDescriptionPrefix()));

    private static final Footprint FOOTPRINT = Footprint.declare(Assemblers.SHAPE, BLOCK, PART, ITEM);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChemicalPlantBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("chemical_plant", () -> new BlockEntityType<>(ChemicalPlantBlockEntity::new, BLOCK.get()));

    public static final Supplier<MenuType<ChemicalPlantMenu>> MENU =
            MENUS.register("chemical_plant", () -> IMenuTypeExtension.create(ChemicalPlantMenu::new));

    private ChemicalPlants() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(ChemicalPlants::registerCapabilities);
    }

    /** The plant's footprint. */
    public static Footprint footprint() {
        return FOOTPRINT;
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // On every face of every block of the footprint: Groundworks' parts forward their lookups here.
        event.registerBlockEntity(Capabilities.Item.BLOCK, BLOCK_ENTITY.get(), (machine, side) -> machine.itemFace());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BLOCK_ENTITY.get(), (machine, side) -> machine.energyFace());
        // Fluid is not forwarded whole as those are: only the four connection blocks answer, on their one outward
        // face, and a part's lookup sees no more than its position. The origin has none, so Groundworks' forward
        // finds nothing there and falls through to this one.
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, entity, side) -> fluidConnection(level, pos, state, side),
                PART.get());
    }

    /** What a part block answers to a fluid lookup: its plant's boxes if the block is a Fluid Connection and the face is its own. */
    private static @Nullable ResourceHandler<FluidResource> fluidConnection(Level level, BlockPos pos, BlockState state,
            @Nullable Direction side) {
        if (side == null || !(state.getBlock() instanceof FootprintPartBlock part)) {
            return null;
        }
        BlockPos origin = part.footprint().standingOrigin(level, pos, state);
        return origin != null && level.getBlockEntity(origin) instanceof ChemicalPlantBlockEntity machine
                ? machine.fluidConnection(pos, side) : null;
    }
}
