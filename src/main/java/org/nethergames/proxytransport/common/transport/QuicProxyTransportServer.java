/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.nethergames.proxytransport.common.network.PeerFactory;
import org.nethergames.proxytransport.common.network.QuicStreamChannelInitializer;
import org.nethergames.proxytransport.common.util.SelfSignedCertificateGenerator;

/**
 * A ProxyTransport listener over QUIC; each bidirectional stream becomes a host session.
 * <p>
 * Requires the QUIC native, normally injected by
 * {@link org.nethergames.proxytransport.common.util.QuicLibraryInstaller}.
 */
public final class QuicProxyTransportServer {

    private static final String ALPN = "ng";

    private final TransportLogger logger;

    private EventLoopGroup group;
    private Channel channel;

    public QuicProxyTransportServer(TransportLogger logger) {
        this.logger = logger;
    }

    /**
     * @param certCommonName CN of the ephemeral self-signed certificate; the proxy trusts any certificate
     * @return a future completing on successful bind, or completing exceptionally on failure
     */
    public CompletableFuture<Void> bind(InetSocketAddress bindAddress, PeerFactory peerFactory,
                                        String certCommonName) {
        CompletableFuture<Void> result = new CompletableFuture<>();

        final QuicSslContext sslContext;
        try {
            // Throwable, not Exception: a missing native surfaces as UnsatisfiedLinkError.
            SelfSignedCertificateGenerator.Result certificate =
                SelfSignedCertificateGenerator.generate(certCommonName);
            sslContext = QuicSslContextBuilder.forServer(certificate.privateKey(), null, certificate.certificate())
                .applicationProtocols(ALPN)
                .build();
        } catch (Throwable t) {
            this.logger.error("ProxyTransport QUIC could not initialise", t);
            result.completeExceptionally(t);
            return result;
        }

        this.group = TransportEventLoops.newGroup(0, new DefaultThreadFactory("ProxyTransport-QUIC"));

        ChannelHandler codec = new QuicServerCodecBuilder()
            .sslContext(sslContext)
            .maxIdleTimeout(30, TimeUnit.SECONDS)
            .initialMaxData(10_000_000)
            .initialMaxStreamDataBidirectionalLocal(1_000_000)
            .initialMaxStreamDataBidirectionalRemote(1_000_000)
            .initialMaxStreamsBidirectional(256)
            .initialMaxStreamsUnidirectional(256)
            .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
            // The connection-level handler must be @Sharable; sessions are created per stream instead.
            .handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                }
            })
            .streamHandler(new QuicStreamChannelInitializer(peerFactory))
            .build();

        new Bootstrap()
            .group(this.group)
            .channel(TransportEventLoops.datagramChannel())
            .handler(codec)
            .bind(bindAddress)
            .addListener((ChannelFuture future) -> {
                if (future.isSuccess()) {
                    this.channel = future.channel();
                    this.logger.info("ProxyTransport QUIC listening on " + bindAddress);
                    result.complete(null);
                } else {
                    this.logger.error("ProxyTransport QUIC failed to bind on " + bindAddress, future.cause());
                    shutdownGroup();
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
        shutdownGroup();
    }

    private void shutdownGroup() {
        if (this.group != null) {
            this.group.shutdownGracefully();
            this.group = null;
        }
    }
}