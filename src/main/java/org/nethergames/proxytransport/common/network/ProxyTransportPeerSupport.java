/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.nethergames.proxytransport.common.codec.ProxyTransportCompressionCodec;
import org.nethergames.proxytransport.common.codec.ProxyTransportCompressionStrategy;

/**
 * Shared ProxyTransport peer behaviour. Hosts extend their own {@link BedrockPeer} subclass and delegate here so
 * the wire behaviour stays identical across hosts.
 */
public final class ProxyTransportPeerSupport {

    /**
     * Reported to callers that still query a RakNet version (e.g. compression selection); a ProxyTransport link
     * has no RakNet channel.
     */
    public static final int RAK_VERSION = 11;

    /** Protocol version (1.20.60) from which the compression byte is prefixed to outgoing batches. */
    private static final int MIN_PREFIXED_COMPRESSION_PROTOCOL = 649;

    private ProxyTransportPeerSupport() {
    }

    /**
     * Installs the Zstd-aware compression codec, replacing any codec already in the pipeline.
     * <p>
     * Prefixing is asymmetric, mirroring the proxy: the compression byte is only written once compression is
     * negotiated and the client is &ge; 1.20.60, never before {@code NetworkSettings}. Decoding always reads it.
     */
    public static void installCompression(Channel channel, CompressionStrategy strategy, int protocolVersion) {
        boolean encodePrefixed = protocolVersion >= MIN_PREFIXED_COMPRESSION_PROTOCOL;
        CompressionStrategy wrapped = new ProxyTransportCompressionStrategy(strategy.getDefaultCompression());
        ProxyTransportCompressionCodec codec = new ProxyTransportCompressionCodec(wrapped, encodePrefixed);

        ChannelPipeline pipeline = channel.pipeline();
        ChannelHandler existing = pipeline.get(CompressionCodec.NAME);
        if (existing == null) {
            pipeline.addBefore(BedrockBatchDecoder.NAME, CompressionCodec.NAME, codec);
        } else {
            pipeline.replace(CompressionCodec.NAME, CompressionCodec.NAME, codec);
        }
    }

    /**
     * Answers the proxy's round-trip probe, a {@link NetworkStackLatencyPacket} with a zero timestamp. It is not
     * a client packet and must not reach the host's session.
     *
     * @return {@code true} if handled, meaning the caller must stop processing the packet
     */
    public static boolean tryHandleLatencyProbe(BedrockPeer peer, BedrockPacketWrapper wrapper) {
        if (wrapper.getPacket() instanceof NetworkStackLatencyPacket latency && latency.getTimestamp() == 0L) {
            NetworkStackLatencyPacket response = new NetworkStackLatencyPacket();
            response.setTimestamp(0L);
            response.setFromServer(true);
            peer.sendPacketImmediately(0, wrapper.getSenderSubClientId(), response);
            return true;
        }
        return false;
    }
}