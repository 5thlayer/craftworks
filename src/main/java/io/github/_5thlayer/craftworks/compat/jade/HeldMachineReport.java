// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jade;

import java.util.Locale;
import java.util.Optional;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.MachineState;
import io.github._5thlayer.craftworks.machine.AssemblerBlockEntity;
import io.github._5thlayer.craftworks.machine.HeldRecipes;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.api.view.ProgressView;

/**
 * What the server tells Jade about an Assembler: its Held recipe, its {@link MachineState}
 * and how far its craft is. The state is the block entity's own ({@link AssemblerBlockEntity#state}), worked out on the
 * server where the checks are and never repeated here.
 *
 * <p>The {@link Client} draws it. They are apart so that a dedicated server never loads Jade's drawing.
 */
class HeldMachineReport implements StreamServerDataProvider<BlockAccessor, HeldMachineReport.Data> {

    static final HeldMachineReport INSTANCE = new HeldMachineReport();

    // Jade keys a player's on/off setting for this tooltip by it.
    private static final Identifier UID = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "assembler");

    private static final StreamCodec<ByteBuf, MachineState> STATE_CODEC =
            ByteBufCodecs.idMapper(ordinal -> MachineState.values()[ordinal], MachineState::ordinal);

    private static final StreamCodec<RegistryFriendlyByteBuf, Data> CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), Data::held,
            ItemStack.OPTIONAL_STREAM_CODEC, Data::product,
            STATE_CODEC, Data::state,
            ByteBufCodecs.FLOAT, Data::progress,
            Data::new);

    /**
     * @param held     the Held recipe's id, if there is one
     * @param product  what it makes, empty when the recipe is gone after a reload
     * @param progress how far the craft under way is, 0 to 1: the screen's bar
     */
    record Data(Optional<Identifier> held, ItemStack product, MachineState state, float progress) {
    }

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel server)) {
            return null;
        }
        AssemblerBlockEntity machine = accessor.typedBlockEntity();
        Optional<Identifier> held = machine.heldRecipe();
        ItemStack product = held.flatMap(id -> HeldRecipes.find(server, id))
                .map(recipe -> recipe.value().product())
                .orElse(ItemStack.EMPTY);
        int duration = machine.craftDuration();
        float progress = duration == 0 ? 0 : Math.min(1, (float) machine.craftProgress() / duration);
        return new Data(held, product, machine.state(), progress);
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return CODEC;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /**
     * Draws the report: the Held recipe's product, then its state, with a progress bar while it crafts. The
     * fluid boxes are Jade's own bars, from {@link AssemblerFluidView}.
     */
    static final class Client extends HeldMachineReport implements IBlockComponentProvider {

        static final Client INSTANCE = new Client();

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            decodeFromData(accessor).ifPresent(report -> {
                if (report.held().isEmpty()) {
                    tooltip.add(Component.translatable("craftworks.jade.no_held_recipe").withStyle(ChatFormatting.GRAY));
                } else if (report.product().isEmpty()) {
                    tooltip.add(Component.literal(report.held().get().toString()));
                } else {
                    tooltip.add(JadeUI.smallItem(report.product()));
                    tooltip.append(report.product().getHoverName());
                }
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
