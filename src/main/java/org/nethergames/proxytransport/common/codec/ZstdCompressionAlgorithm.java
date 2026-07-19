/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.codec;

import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;

/**
 * The Zstandard compression algorithm. ProxyTransport extends the Bedrock compression-type byte with the
 * value {@code 254} ({@code -2}) to signal Zstd, which vanilla Bedrock does not support. This marker enum lets
 * the strategy/codec recognise Zstd alongside the vanilla {@code PacketCompressionAlgorithm} values.
 */
public enum ZstdCompressionAlgorithm implements CompressionAlgorithm {
    ZSTD
}