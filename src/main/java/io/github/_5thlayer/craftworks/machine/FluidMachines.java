// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;
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
 * The registrations of every {@link FluidMachine} (#26, #27): for each, a block and an item standing on a Groundworks
 * footprint of their own, a block entity type, and the menu. Each keeps its Held recipe on the item in the
 * Assemblers' component. A machine's registry ids come from its {@link MachineDefaults#blockName()}: the block, the
 * item, the block entity type and the menu are that, and its part block that and {@code _part}.
 *
 * <p>A new fluid machine is a {@link FluidMachine} constant, listed in {@link FluidMachine#ALL}, and an
 * {@link #entry} here with its footprint; the rest, the config, the viewers' tabs, the screen, Jade and the
 * creative tab, walks {@link #all()}.
 */
public final class FluidMachines {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Craftworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Craftworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Craftworks.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Craftworks.MOD_ID);

    /** Factorio's chemical plant, on the Assemblers' footprint: 3x3, two blocks tall. */
    public static final Entry CHEMICAL_PLANT = entry(FluidMachine.CHEMICAL_PLANT, FootprintShape.square(3, 2));

    /** Factorio's oil refinery: 5x5 on the ground and three blocks tall, 74 parts. */
    public static final Entry OIL_REFINERY = entry(FluidMachine.OIL_REFINERY, FootprintShape.square(5, 3));

    private static final List<Entry> ALL = List.of(CHEMICAL_PLANT, OIL_REFINERY);

    private FluidMachines() {
    }

    /** Every fluid machine, in the order the creative tab lists them. */
    public static List<Entry> all() {
        return ALL;
    }

    /** The registrations of the machine of this kind, which must be a fluid machine. */
    public static Entry of(MachineKind kind) {
        return ALL.stream().filter(entry -> entry.machine().kind() == kind).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(kind + " is not a fluid machine"));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(FluidMachines::registerCapabilities);
    }

    private static Entry entry(FluidMachine machine, FootprintShape shape) {
        return new Entry(machine, shape);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (Entry entry : ALL) {
            entry.registerCapabilities(event);
        }
    }

    /** One machine's registered block, part, item, block entity type and menu, and its footprint. */
    public static final class Entry {

        private final FluidMachine machine;
        private final DeferredBlock<FluidMachineBlock> block;
        private final DeferredBlock<FootprintPartBlock> part;
        private final DeferredItem<FootprintItem> item;
        private final Footprint footprint;
        private final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> blockEntityType;
        private final Supplier<MenuType<FluidMachineMenu>> menu;

        private Entry(FluidMachine machine, FootprintShape shape) {
            this.machine = machine;
            String name = machine.defaults().blockName();
            this.block = BLOCKS.registerBlock(name, properties -> new FluidMachineBlock(Assemblers.machine(properties), this));
            this.part = BLOCKS.registerBlock(name + "_part",
                    properties -> new FootprintPartBlock(Assemblers.machine(properties).noLootTable(), this::footprint));
            this.item = ITEMS.registerItem(name,
                    properties -> new FootprintItem(footprint(), properties.useBlockDescriptionPrefix()));
            this.footprint = Footprint.declare(shape, block, part, item);
            this.blockEntityType = BLOCK_ENTITIES.register(name,
                    () -> new BlockEntityType<>(this::blockEntity, block.get()));
            this.menu = MENUS.register(name, () -> IMenuTypeExtension.create((containerId, playerInventory, buffer) ->
                    new FluidMachineMenu(menu().get(), containerId, playerInventory, buffer, machine)));
        }

        public FluidMachine machine() {
            return machine;
        }

        public DeferredBlock<FluidMachineBlock> block() {
            return block;
        }

        public DeferredItem<FootprintItem> item() {
            return item;
        }

        public DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> blockEntityType() {
            return blockEntityType;
        }

        public Supplier<MenuType<FluidMachineMenu>> menu() {
            return menu;
        }

        /** The machine's footprint. */
        public Footprint footprint() {
            return footprint;
        }

        /** The machine's block entity: the fluid machine, as {@link #machine()} describes it. */
        public FluidMachineBlockEntity blockEntity(BlockPos pos, BlockState state) {
            return new FluidMachineBlockEntity(blockEntityType.get(), pos, state, machine, menu);
        }

        private void registerCapabilities(RegisterCapabilitiesEvent event) {
            // On every face of every block of the footprint: Groundworks' parts forward their lookups here.
            event.registerBlockEntity(Capabilities.Energy.BLOCK, blockEntityType.get(), (entity, side) -> entity.energyFace());
            if (machine.hasItemSlots()) {
                event.registerBlockEntity(Capabilities.Item.BLOCK, blockEntityType.get(), (entity, side) -> entity.itemFace());
            }
            // Fluid is not forwarded whole as those are: only the connection blocks answer, on their one outward
            // face, and a part's lookup sees no more than its position. The origin has none, so Groundworks' forward
            // finds nothing there and falls through to this one.
            event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, entity, side) -> fluidConnection(level, pos, state, side),
                    part.get());
        }

        /** What a part block answers to a fluid lookup: its machine's boxes if the block is a Fluid Connection and the face is its own. */
        private static @Nullable ResourceHandler<FluidResource> fluidConnection(Level level, BlockPos pos, BlockState state,
                @Nullable Direction side) {
            if (side == null || !(state.getBlock() instanceof FootprintPartBlock part)) {
                return null;
            }
            BlockPos origin = part.footprint().standingOrigin(level, pos, state);
            return origin != null && level.getBlockEntity(origin) instanceof FluidMachineBlockEntity entity
                    ? entity.fluidConnection(pos, side) : null;
        }
    }
}
