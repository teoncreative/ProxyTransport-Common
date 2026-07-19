/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.network;

import io.netty.channel.Channel;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;

/**
 * Creates the {@link BedrockPeer} that terminates a ProxyTransport connection.
 * <p>
 * Hosts have differing peer base classes, so they supply a fully-formed peer here rather than this library
 * abstracting sessions. Implementations should apply {@link ProxyTransportPeerSupport}.
 */
@FunctionalInterface
public interface PeerFactory {

    BedrockPeer createPeer(Channel channel);
}