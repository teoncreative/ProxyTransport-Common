/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.transport;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/** A ProxyTransport listener over plain TCP. Each accepted socket becomes a host session. */
public final class TcpProxyTransportServer {

    private final TransportLogger logger;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public TcpProxyTransportServer(TransportLogger logger) {
        this.logger = logger;
    }

    /**
     * @return a future completing on successful bind, or completing exceptionally on failure. Hosts that must
     * not abort startup on a bind failure can swallow it.
     */
    public CompletableFuture<Void> bind(InetSocketAddress bindAddress, ChannelInitializer<Channel> childHandler) {
        boolean epoll = Epoll.isAvailable();
        this.bossGroup = epoll
            ? new EpollEventLoopGroup(1, new DefaultThreadFactory("ProxyTransport-TCP-Boss"))
            : new NioEventLoopGroup(1, new DefaultThreadFactory("ProxyTransport-TCP-Boss"));
        this.workerGroup = epoll
            ? new EpollEventLoopGroup(new DefaultThreadFactory("ProxyTransport-TCP-Worker"))
            : new NioEventLoopGroup(new DefaultThreadFactory("ProxyTransport-TCP-Worker"));

        Class<? extends ServerChannel> channelClass = epoll
            ? EpollServerSocketChannel.class
            : NioServerSocketChannel.class;

        CompletableFuture<Void> result = new CompletableFuture<>();
        new ServerBootstrap()
            .group(this.bossGroup, this.workerGroup)
            .channel(channelClass)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(childHandler)
            .bind(bindAddress)
            .addListener((ChannelFuture future) -> {
                if (future.isSuccess()) {
                    this.channel = future.channel();
                    this.logger.info("ProxyTransport TCP listening on " + bindAddress);
                    result.complete(null);
                } else {
                    this.logger.error("ProxyTransport TCP failed to bind on " + bindAddress, future.cause());
                    shutdownGroups();
                    result.completeExceptionally(future.cause());
                }
            });
        return result;
    }

    public void shutdown() {
        if (this.channel != null) {
            this.channel.close().syncUninterruptibly();
            this.channel = null;
        }
        shutdownGroups();
    }

    private void shutdownGroups() {
        if (this.workerGroup != null) {
            this.workerGroup.shutdownGracefully();
            this.workerGroup = null;
        }
        if (this.bossGroup != null) {
            this.bossGroup.shutdownGracefully();
            this.bossGroup = null;
        }
    }
}