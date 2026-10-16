// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The server's end of a connection for a player with no client: it sends nothing and keeps every packet
 * it was given, so the Mod's syncs go nowhere and a test can read what would have crossed. The client has
 * the channels the test names and no others; which ones a real client has is the test's to say.
 */
final class TestConnection extends ServerGamePacketListenerImpl {

    private final List<Packet<?>> sent = new ArrayList<>();
    private final Set<Identifier> channels;

    private TestConnection(MinecraftServer server, Connection connection, ServerPlayer player, Set<Identifier> channels) {
        super(server, connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        this.channels = channels;
    }

    /** A server player standing in the test, not in the player list, whose client has these payloads' channels. */
    static ServerPlayer player(GameTestHelper helper, CustomPacketPayload.Type<?>... channels) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "craftworks-test"), ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // The listener's constructor makes it the player's connection.
        new TestConnection(level.getServer(), connection, player,
                Stream.of(channels).map(CustomPacketPayload.Type::id).collect(Collectors.toUnmodifiableSet()));
        player.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
        return player;
    }

    /** Every packet the server gave this player, oldest first. */
    static List<Packet<?>> sentTo(ServerPlayer player) {
        return ((TestConnection) player.connection).sent;
    }

    @Override
    public void send(Packet<?> packet) {
        sent.add(packet);
    }

    @Override
    public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
        sent.add(packet);
    }

    @Override
    public boolean hasChannel(Identifier payload) {
        return channels.contains(payload);
    }
}
