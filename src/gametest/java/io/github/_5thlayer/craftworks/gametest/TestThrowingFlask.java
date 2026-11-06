// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * An item whose fluid capability throws, as Just Dire Things' Polymorphic Wand does while a recipe load runs before
 * the server configs it reads are loaded. Registered only in the Mod's own runs, from the {@code gametest} source
 * set, like {@link TestTank}, so no player's world has it.
 */
final class TestThrowingFlask {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Craftworks.MOD_ID);

    static final DeferredItem<Item> ITEM = ITEMS.registerSimpleItem("gametest_throwing_flask");

    private TestThrowingFlask() {
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(TestThrowingFlask::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> {
            throw new IllegalStateException("Cannot get config value before config is loaded.");
        }, ITEM.get());
    }
}
