/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.transport;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import java.util.concurrent.ThreadFactory;

final class KQueueSupport {

    private KQueueSupport() {
    }

    static boolean isAvailable() {
        return KQueue.isAvailable();
    }

    static EventLoopGroup newGroup(int threads, ThreadFactory factory) {
        return new KQueueEventLoopGroup(threads, factory);
    }

    static Class<? extends ServerChannel> serverSocketChannel() {
        return KQueueServerSocketChannel.class;
    }

    static Class<? extends Channel> datagramChannel() {
        return KQueueDatagramChannel.class;
    }
}
