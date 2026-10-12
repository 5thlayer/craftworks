// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jade;


import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.MachineState;
import io.github._5thlayer.craftworks.machine.RefinerBlockEntity;
import io.github._5thlayer.libworks.machine.jade.MachineReport;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.ITooltip;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.api.view.ProgressView;

/**
 * What the server tells Jade about a Refiner: its {@link MachineState} and how far its smelt is. The input and
 * output are Jade's own item list, from the item capability, and the energy its own bar, from the energy one.
 *
 * <p>Sent and decoded by libworks-runtime's {@link MachineReport}; {@link Lines} words it on the client, apart so
 * that a dedicated server never loads Jade's drawing.
 */
final class RefinerReport {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "refiner");

    private static final StreamCodec<ByteBuf, MachineState> STATE_CODEC =
            ByteBufCodecs.idMapper(ordinal -> MachineState.values()[ordinal], MachineState::ordinal);

    private static final StreamCodec<RegistryFriendlyByteBuf, Data> CODEC = StreamCodec.composite(
            STATE_CODEC, Data::state,
            ByteBufCodecs.FLOAT, Data::progress,
            Data::new);

    /** @param progress how far the smelt under way is, 0 to 1 */
    record Data(MachineState state, float progress) {
    }

    static final MachineReport<RefinerBlockEntity, Data> REPORT =
            new MachineReport<>(UID, RefinerBlockEntity.class, RefinerReport::read, CODEC);

    private RefinerReport() {
    }

    private static @Nullable Data read(RefinerBlockEntity refiner) {
        if (!(refiner.getLevel() instanceof ServerLevel)) {
            return null;
        }
        int duration = refiner.smeltDuration();
        float progress = duration == 0 ? 0 : Math.min(1, (float) refiner.smeltProgress() / duration);
        return new Data(refiner.state(), progress);
    }

    /** Words the report: a progress bar while it smelts, and why it does not otherwise. */
    static final class Lines {

        private Lines() {
        }

        static void add(Data report, ITooltip tooltip) {
            switch (report.state()) {
                case NO_RECIPE -> {
                }
                case CRAFTING -> tooltip.add(JadeUI.progress(new ProgressView(
                        ProgressView.Part.of(report.progress()),
                        stateText(report.state()),
                        JadeUI.progressStyle(),
                        BoxStyle.nestedBox())));
                default -> tooltip.add(stateText(report.state()).copy().withStyle(ChatFormatting.RED));
            }
        }

        private static Component stateText(MachineState state) {
            return Component.translatable(state.langKey());
        }
    }
}
