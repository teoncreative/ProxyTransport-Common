/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.codec;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

/**
 * The ProxyTransport wire framing, shared by both ends of the link.
 * <pre>
 * [4-byte big-endian length][1-byte compression type][compressed Bedrock batch]
 * </pre>
 * There is no {@code 0xFE} game-packet byte, unlike RakNet. The compression byte uses the vanilla Bedrock
 * values plus {@link #ZSTD_COMPRESSION_HEADER}.
 */
public final class ProxyTransportFraming {

    public static final String FRAME_DECODER = "frame-decoder";
    public static final String FRAME_ENCODER = "frame-encoder";
    public static final int LENGTH_FIELD_BYTES = 4;

    /** The compression-type byte ProxyTransport adds for Zstd. */
    public static final byte ZSTD_COMPRESSION_HEADER = -2;

    private ProxyTransportFraming() {
    }

    /** Appends the framing handlers; call before the frame and compression codecs. */
    public static void addFraming(ChannelPipeline pipeline) {
        pipeline
            .addLast(FRAME_DECODER, new LengthFieldBasedFrameDecoder(
                Integer.MAX_VALUE, 0, LENGTH_FIELD_BYTES, 0, LENGTH_FIELD_BYTES))
            .addLast(FRAME_ENCODER, new LengthFieldPrepender(LENGTH_FIELD_BYTES));
    }
}