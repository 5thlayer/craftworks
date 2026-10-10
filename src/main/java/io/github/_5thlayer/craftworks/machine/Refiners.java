// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** The Refiner's registrations: its block and item, block entity type and menu. */
public final class Refiners {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Craftworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Craftworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Craftworks.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Craftworks.MOD_ID);

    public static final DeferredBlock<RefinerBlock> BLOCK = BLOCKS.registerBlock("refiner", RefinerBlock::new,
            properties -> properties.strength(3.0f, 6.0f).sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RefinerBlock.LIT) ? 13 : 0));

    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefinerBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("refiner", () -> new BlockEntityType<>(RefinerBlockEntity::new, BLOCK.get()));

    public static final Supplier<MenuType<RefinerMenu>> MENU =
            MENUS.register("refiner", () -> IMenuTypeExtension.create(RefinerMenu::new));

    private Refiners() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(Refiners::registerCapabilities);
    }

    /** The same faces on every side: which way the block looks never changes what a pipe or a cable gets. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, BLOCK_ENTITY.get(), (refiner, side) -> refiner.itemFace());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BLOCK_ENTITY.get(), (refiner, side) -> refiner.energyFace());
    }
}
