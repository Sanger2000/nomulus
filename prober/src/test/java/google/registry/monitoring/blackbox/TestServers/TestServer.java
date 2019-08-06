package google.registry.monitoring.blackbox.TestServers;

import com.google.common.collect.ImmutableList;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;

/**
 * Mock Server Superclass whose subclasses implement specific behaviours we expect blackbox server to perform
 */
public abstract class TestServer {
  private LocalAddress localAddress;

  TestServer(LocalAddress localAddress, ImmutableList<? extends ChannelHandler> handlers) {
    this(new NioEventLoopGroup(1), localAddress, handlers);
  }

  TestServer(EventLoopGroup eventLoopGroup, LocalAddress localAddress, ImmutableList<? extends ChannelHandler> handlers) {
    this.localAddress = localAddress;

    //Creates ChannelInitializer with handlers specified
    ChannelInitializer<LocalChannel> serverInitializer = new ChannelInitializer<LocalChannel>() {
      @Override
      protected void initChannel(LocalChannel ch) {
        for (ChannelHandler handler : handlers) {
          ch.pipeline().addLast(handler);
        }
      }
    };
    //Sets up serverBootstrap with specified initializer, eventLoopGroup, and using LocalServerChannel class
    ServerBootstrap serverBootstrap =
        new ServerBootstrap()
        .group(eventLoopGroup)
        .channel(LocalServerChannel.class)
        .childHandler(serverInitializer);

    ChannelFuture unusedFuture = serverBootstrap.bind(localAddress).syncUninterruptibly();

  }

}
