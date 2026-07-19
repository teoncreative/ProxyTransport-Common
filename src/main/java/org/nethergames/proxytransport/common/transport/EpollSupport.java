/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.transport;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import java.util.concurrent.ThreadFactory;

final class EpollSupport {

    private EpollSupport() {
    }

    static boolean isAvailable() {
        return Epoll.isAvailable();
    }

    static EventLoopGroup newGroup(int threads, ThreadFactory factory) {
        return new EpollEventLoopGroup(threads, factory);
    }

    static Class<? extends ServerChannel> serverSocketChannel() {
        return EpollServerSocketChannel.class;
    }

    static Class<? extends Channel> datagramChannel() {
        return EpollDatagramChannel.class;
    }
}
