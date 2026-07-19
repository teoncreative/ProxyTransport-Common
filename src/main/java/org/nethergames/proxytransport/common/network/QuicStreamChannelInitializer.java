/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.network;

import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicConnectionPathStats;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;

/**
 * QUIC stream pipeline. Beyond the shared Bedrock pipeline it polls the connection's path statistics for a
 * round-trip latency. All QUIC types stay confined here so {@link TransportChannelInitializer} loads without
 * them.
 */
public class QuicStreamChannelInitializer extends TransportChannelInitializer {

    private static final int PING_CYCLE_SECONDS = 2;

    public QuicStreamChannelInitializer(PeerFactory peerFactory) {
        super(peerFactory);
    }

    @Override
    protected void onPeerCreated(Channel channel, BedrockPeer peer) {
        if (!(channel instanceof QuicStreamChannel streamChannel) || !(peer instanceof ProxyTransportPeer target)) {
            return;
        }

        // Neither the stream address nor the connection id is an InetSocketAddress, which hosts assume; the
        // real UDP address stands in until the proxy's login forwarding replaces it with the player IP.
        SocketAddress remote = streamChannel.parent().remoteSocketAddress();
        if (remote instanceof InetSocketAddress inet) {
            target.setProxiedAddress(inet);
        }

        ScheduledFuture<?> task = streamChannel.eventLoop().scheduleAtFixedRate(() ->
            streamChannel.parent().collectPathStats(0).addListener((Future<QuicConnectionPathStats> future) -> {
                if (future.isSuccess()) {
                    QuicConnectionPathStats stats = future.getNow();
                    target.setPingMillis((int) (stats.rtt() / 1_000_000L));
                }
            }), PING_CYCLE_SECONDS, PING_CYCLE_SECONDS, TimeUnit.SECONDS);

        streamChannel.closeFuture().addListener(f -> task.cancel(false));
    }
}