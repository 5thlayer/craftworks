// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.groundworks.FastReplace;
import io.github._5thlayer.groundworks.Footprint;
import io.github._5thlayer.groundworks.FootprintItem;
import io.github._5thlayer.groundworks.FootprintPartBlock;
import io.github._5thlayer.groundworks.FootprintShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
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
 * The Assemblers' registrations: a block and an item for each tier, each standing on a Groundworks
 * footprint of its own, one shared block entity type, the menu, and the component that carries the Held
 * recipe on the item.
 *
 * <p>The footprint is 3x2x3, Factorio's tile square two blocks tall, the origin at its bottom centre. Each tier
 * has a part block of its own, since a part names the footprint it belongs to; the Mod registers them from
 * Groundworks' class and declares each footprint at construction, on both sides.
 */
public final class Assemblers {

    /** The Assemblers' footprint. */
    static final FootprintShape SHAPE = FootprintShape.square(3, 2);

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Craftworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Craftworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Craftworks.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Craftworks.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Craftworks.MOD_ID);

    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Craftworks.MOD_ID);

    private static final Map<AssemblerTier, Footprint> FOOTPRINTS = new EnumMap<>(AssemblerTier.class);
    private static final Map<AssemblerTier, DeferredBlock<AssemblerBlock>> BLOCK_BY_TIER = new EnumMap<>(AssemblerTier.class);
    private static final Map<AssemblerTier, DeferredBlock<FootprintPartBlock>> PART_BY_TIER = new EnumMap<>(AssemblerTier.class);
    private static final Map<AssemblerTier, DeferredItem<FootprintItem>> ITEM_BY_TIER = new EnumMap<>(AssemblerTier.class);

    static {
        for (AssemblerTier tier : AssemblerTier.values()) {
            DeferredBlock<AssemblerBlock> origin = BLOCKS.registerBlock(tier.blockName(),
                    properties -> new AssemblerBlock(machine(properties), tier));
            DeferredBlock<FootprintPartBlock> part = BLOCKS.registerBlock(tier.partBlockName(),
                    properties -> new FootprintPartBlock(machine(properties).noLootTable(), () -> footprint(tier)));
            DeferredItem<FootprintItem> item = ITEMS.registerItem(tier.blockName(),
                    properties -> new FootprintItem(footprint(tier), properties.useBlockDescriptionPrefix()));
            BLOCK_BY_TIER.put(tier, origin);
            PART_BY_TIER.put(tier, part);
            ITEM_BY_TIER.put(tier, item);
            FOOTPRINTS.put(tier, Footprint.declare(SHAPE, origin, part, item));
        }
    }

    /** One block entity type for all three tiers. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblerBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("assembler", () -> new BlockEntityType<>(AssemblerBlockEntity::new,
                    BLOCK_BY_TIER.values().stream().map(Supplier::get).toArray(Block[]::new)));

    public static final Supplier<MenuType<AssemblerMenu>> MENU =
            MENUS.register("assembler", () -> IMenuTypeExtension.create(AssemblerMenu::new));

    /** The Held recipe's id, on the item and so kept when the block is broken and placed again. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> HELD_RECIPE =
            COMPONENTS.register("held_recipe", () -> DataComponentType.<Identifier>builder()
                    .persistent(Identifier.CODEC)
                    .networkSynchronized(Identifier.STREAM_CODEC)
                    .build());

    /** Creative-only: no recipe, and the creative tab is the only way to get one. */
    public static final DeferredBlock<CreativeEnergySourceBlock> CREATIVE_ENERGY_SOURCE =
            BLOCKS.registerBlock("creative_energy_source", CreativeEnergySourceBlock::new,
                    properties -> properties.strength(3.0f, 6.0f).sound(SoundType.METAL));

    public static final DeferredItem<BlockItem> CREATIVE_ENERGY_SOURCE_ITEM =
            ITEMS.registerSimpleBlockItem(CREATIVE_ENERGY_SOURCE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeEnergySourceBlockEntity>> CREATIVE_ENERGY_SOURCE_ENTITY =
            BLOCK_ENTITIES.register("creative_energy_source", () -> new BlockEntityType<>(CreativeEnergySourceBlockEntity::new,
                    CREATIVE_ENERGY_SOURCE.get()));

    /** Creative-only like the energy one: the fluid twin, set with a bucket (#32). */
    public static final DeferredBlock<CreativeFluidSourceBlock> CREATIVE_FLUID_SOURCE =
            BLOCKS.registerBlock("creative_fluid_source", CreativeFluidSourceBlock::new,
                    properties -> properties.strength(3.0f, 6.0f).sound(SoundType.METAL));

    public static final DeferredItem<BlockItem> CREATIVE_FLUID_SOURCE_ITEM =
            ITEMS.registerSimpleBlockItem(CREATIVE_FLUID_SOURCE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeFluidSourceBlockEntity>> CREATIVE_FLUID_SOURCE_ENTITY =
            BLOCK_ENTITIES.register("creative_fluid_source", () -> new BlockEntityType<>(CreativeFluidSourceBlockEntity::new,
                    CREATIVE_FLUID_SOURCE.get()));

    /**
     * Craftworks' own creative tab, with the Assembler 1 as its icon. Every Craftworks item sits in it and in
     * no other tab; a new item gets its place with one line in the list.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            CREATIVE_TABS.register("craftworks", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup." + Craftworks.MOD_ID))
                    .icon(() -> new ItemStack(item(AssemblerTier.ONE).get()))
                    .displayItems((parameters, output) -> {
                        ITEM_BY_TIER.values().forEach(output::accept);
                        output.accept(CREATIVE_ENERGY_SOURCE_ITEM.get());
                        output.accept(CREATIVE_FLUID_SOURCE_ITEM.get());
                    })
                    .build());

    private Assemblers() {
    }

    /**
     * The origin draws the whole machine from its own position, and a face of its model off the origin's own
     * cube is lit by the light there: an occluding origin holds none, and the machine renders dark.
     */
    static BlockBehaviour.Properties machine(BlockBehaviour.Properties properties) {
        return properties.strength(3.0f, 6.0f).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        CREATIVE_TABS.register(modBus);
        modBus.addListener(Assemblers::registerCapabilities);
        // On both sides: the preview plans on the client and the click on the server (Groundworks' ADR 0008).
        FastReplace.group(Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "assembler"),
                block -> block instanceof AssemblerBlock
                        || block instanceof FootprintPartBlock part && FOOTPRINTS.containsValue(part.footprint()),
                new AssemblerReplace());
    }

    /** The tier's footprint. */
    public static Footprint footprint(AssemblerTier tier) {
        return FOOTPRINTS.get(tier);
    }

    public static DeferredBlock<AssemblerBlock> block(AssemblerTier tier) {
        return BLOCK_BY_TIER.get(tier);
    }

    public static DeferredItem<FootprintItem> item(AssemblerTier tier) {
        return ITEM_BY_TIER.get(tier);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // On every face of every block of the footprint: Groundworks' parts forward their lookups here.
        event.registerBlockEntity(Capabilities.Item.BLOCK, BLOCK_ENTITY.get(), (machine, side) -> machine.itemFace());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BLOCK_ENTITY.get(), (machine, side) -> machine.energyFace());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CREATIVE_ENERGY_SOURCE_ENTITY.get(), (source, side) -> source.energyFace());
        // Fluid is not forwarded whole as those are: only the connection blocks answer, on their one outward
        // face, and a part's lookup sees no more than its position. The origin has none, so Groundworks' forward
        // finds nothing there and falls through to this one.
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, entity, side) -> fluidConnection(level, pos, state, side),
                PART_BY_TIER.values().stream().map(Supplier::get).toArray(Block[]::new));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, CREATIVE_FLUID_SOURCE_ENTITY.get(), (source, side) -> source.fluidFace());
    }

    /**
     * What a part block answers to a fluid lookup: its machine's Fluid Connection if the block is one and the
     * face is its own.
     */
    private static @Nullable ResourceHandler<FluidResource> fluidConnection(Level level, BlockPos pos, BlockState state,
            @Nullable Direction side) {
        if (side == null || !(state.getBlock() instanceof FootprintPartBlock part)) {
            return null;
        }
        BlockPos origin = part.footprint().standingOrigin(level, pos, state);
        return origin != null && level.getBlockEntity(origin) instanceof AssemblerBlockEntity machine
                ? machine.fluidPort().connection(pos, side) : null;
    }
}
