/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.transport;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.util.concurrent.ThreadFactory;

/**
 * Picks the platform's native transport - epoll on Linux, kqueue on macOS/BSD - falling back to NIO.
 * <p>
 * Hosts do not all ship the native transport artifacts, so those types cannot be referenced directly from the
 * transports, the JVM would fail to link them. They are confined to {@link EpollSupport} and
 * {@link KQueueSupport}, which are only touched once their classes are known to exist.
 */
public final class TransportEventLoops {

    private enum Type {
        EPOLL,
        KQUEUE,
        NIO
    }

    private static final Type TYPE = detect();

    private TransportEventLoops() {
    }

    private static Type detect() {
        if (isPresent("io.netty.channel.epoll.Epoll") && EpollSupport.isAvailable()) {
            return Type.EPOLL;
        }
        if (isPresent("io.netty.channel.kqueue.KQueue") && KQueueSupport.isAvailable()) {
            return Type.KQUEUE;
        }
        return Type.NIO;
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, TransportEventLoops.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The transport in use, for logging. */
    public static String name() {
        return TYPE.name().toLowerCase();
    }

    public static EventLoopGroup newGroup(int threads, ThreadFactory factory) {
        return switch (TYPE) {
            case EPOLL -> EpollSupport.newGroup(threads, factory);
            case KQUEUE -> KQueueSupport.newGroup(threads, factory);
            case NIO -> new NioEventLoopGroup(threads, factory);
        };
    }

    public static Class<? extends ServerChannel> serverSocketChannel() {
        return switch (TYPE) {
            case EPOLL -> EpollSupport.serverSocketChannel();
            case KQUEUE -> KQueueSupport.serverSocketChannel();
            case NIO -> NioServerSocketChannel.class;
        };
    }

    public static Class<? extends Channel> datagramChannel() {
        return switch (TYPE) {
            case EPOLL -> EpollSupport.datagramChannel();
            case KQUEUE -> KQueueSupport.datagramChannel();
            case NIO -> NioDatagramChannel.class;
        };
    }
}
