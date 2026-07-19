/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.PacketDirection;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchEncoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.NoopCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.nethergames.proxytransport.common.codec.ProxyTransportCompressionCodec;
import org.nethergames.proxytransport.common.codec.ProxyTransportCompressionStrategy;
import org.nethergames.proxytransport.common.codec.ProxyTransportFrameCodec;
import org.nethergames.proxytransport.common.codec.ProxyTransportFraming;

/**
 * Builds the per-connection (TCP socket or QUIC stream) pipeline for a downstream ProxyTransport connection.
 * The host is the Bedrock server, so its packets are {@link PacketDirection#CLIENT_BOUND}.
 * <p>
 * References no QUIC types, so it loads on a TCP-only deployment where the QUIC libraries are absent; QUIC
 * wiring belongs in a subclass overriding {@link #onPeerCreated(Channel, BedrockPeer)}.
 */
public class TransportChannelInitializer extends ChannelInitializer<Channel> {

    private final PeerFactory peerFactory;

    public TransportChannelInitializer(PeerFactory peerFactory) {
        this.peerFactory = peerFactory;
    }

    @Override
    protected void initChannel(Channel channel) {
        channel.attr(PacketDirection.ATTRIBUTE).set(PacketDirection.CLIENT_BOUND);

        ProxyTransportFraming.addFraming(channel.pipeline());

        channel.pipeline()
            .addLast(ProxyTransportFrameCodec.NAME, new ProxyTransportFrameCodec())
            // Encodes unprefixed until NetworkSettings; decode always reads the byte. The host swaps in the
            // negotiated codec via ProxyTransportPeerSupport#installCompression.
            .addLast(CompressionCodec.NAME, new ProxyTransportCompressionCodec(
                new ProxyTransportCompressionStrategy(new NoopCompression()), false))
            .addLast(BedrockBatchDecoder.NAME, new BedrockBatchDecoder())
            .addLast(BedrockBatchEncoder.NAME, new BedrockBatchEncoder())
            .addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3());

        BedrockPeer peer = this.peerFactory.createPeer(channel);
        channel.pipeline().addLast(BedrockPeer.NAME, peer);

        onPeerCreated(channel, peer);
    }

    protected void onPeerCreated(Channel channel, BedrockPeer peer) {
    }
}