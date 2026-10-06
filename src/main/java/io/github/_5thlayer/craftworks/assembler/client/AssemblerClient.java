// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler.client;

import java.util.List;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.assembler.PlanQueueView;
import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.github._5thlayer.craftworks.assembler.ReadyRecipeIds;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.ChemicalPlants;
import io.github._5thlayer.craftworks.machine.client.AssemblerScreen;
import io.github._5thlayer.craftworks.machine.client.FluidMachineScreen;
import io.github._5thlayer.craftworks.network.QueueSyncPacket;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client half of the Personal Assembler: the queue beside the hotbar, and the inventory screen
 * that is the Assembler (ADR-0005).
 *
 * <p>Called only from {@code CraftworksClient}, so nothing here is loaded on a dedicated server.
 */
public final class AssemblerClient {

    private AssemblerClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AssemblerClient::registerHud);
        modBus.addListener(AssemblerClient::registerScreens);
        NeoForge.EVENT_BUS.addListener(AssemblerClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(InventoryWatch::onClientTick);
        NeoForge.EVENT_BUS.addListener(InventoryGridBlank::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(InventoryGridBlank::onScreenInit);
        NeoForge.EVENT_BUS.addListener(InventoryGridBlank::onScreenForeground);
        NeoForge.EVENT_BUS.addListener(InventoryQueue::onRender);
        NeoForge.EVENT_BUS.addListener(InventoryQueue::onTooltip);
        NeoForge.EVENT_BUS.addListener(InventoryQueue::onClick);
    }

    /**
     * Forgets the queue on leaving a world. The view is only ever replaced by a sync, and a server
     * without Craftworks never sends one, so the last world's rows would stay beside the hotbar. The
     * Ready set goes the same way (ADR-0012), or the next world's craftables would list the last one's.
     */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PlanQueueView.accept(new QueueSyncPacket(List.of(), false));
        ReadyRecipeIds.clear();
        InventoryWatch.reset();
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(PersonalAssembler.CRAFTING_PLAN.get(), CraftingPlanScreen::new);
        event.register(Assemblers.MENU.get(), AssemblerScreen::new);
        event.register(ChemicalPlants.MENU.get(), FluidMachineScreen::new);
    }

    /** Above the hotbar in draw order, so the queue is not painted under it. */
    private static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "assembler_queue"),
                new AssemblerHud());
    }
}
