/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.network;

import java.net.SocketAddress;

/** 
 * Transport-driven callbacks a host's peer exposes. Implemented by host peers, fed by the transports. 
 */
public interface ProxyTransportPeer {

    void setPingMillis(int pingMillis);

    /**
     * Seeds the peer with the underlying connection's remote address. A QUIC stream's own address is a stream
     * address and the connection's is a connection id, neither of which is a usable remote address.
     */
    void setProxiedAddress(SocketAddress address);
}