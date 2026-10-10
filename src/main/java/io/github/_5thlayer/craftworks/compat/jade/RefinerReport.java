// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jade;

import java.util.Locale;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.MachineState;
import io.github._5thlayer.craftworks.machine.RefinerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.api.view.ProgressView;

/**
 * What the server tells Jade about a Refiner: its {@link MachineState} and how far its smelt is. The input and
 * output are Jade's own item list, from the item capability, and the energy its own bar, from the energy one.
 *
 * <p>The {@link Client} draws it. They are apart so that a dedicated server never loads Jade's drawing.
 */
class RefinerReport implements StreamServerDataProvider<BlockAccessor, RefinerReport.Data> {

    static final RefinerReport INSTANCE = new RefinerReport();

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

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel)) {
            return null;
        }
        RefinerBlockEntity refiner = accessor.typedBlockEntity();
        int duration = refiner.smeltDuration();
        float progress = duration == 0 ? 0 : Math.min(1, (float) refiner.smeltProgress() / duration);
        return new Data(refiner.state(), progress);
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return CODEC;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** Draws the report: a progress bar while it smelts, and why it does not otherwise. */
    static final class Client extends RefinerReport implements IBlockComponentProvider {

        static final Client INSTANCE = new Client();

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            decodeFromData(accessor).ifPresent(report -> {
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
            });
        }

        private static Component stateText(MachineState state) {
            return Component.translatable("craftworks.jade.state." + state.name().toLowerCase(Locale.ROOT));
        }
    }
}
