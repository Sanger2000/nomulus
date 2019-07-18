// Copyright 2019 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.monitoring.blackbox.TestServers;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Mock Server Superclass whose subclasses implement specific behaviors we expect blackbox server to perform
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
  /**
   * A handler that echoes back its inbound message. The message is also saved in a promise for
   * inspection later.
   */
  public static class EchoHandler extends ChannelInboundHandlerAdapter {

    private final CompletableFuture<String> requestFuture = new CompletableFuture<>();

    public Future<String> getRequestFuture() {
      return requestFuture;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      // In the test we only send messages of type ByteBuf.

      assertThat(msg).isInstanceOf(ByteBuf.class);
      String request = ((ByteBuf) msg).toString(UTF_8);
      // After the message is written back to the client, fulfill the promise.
      ChannelFuture unusedFuture =
          ctx.writeAndFlush(msg).addListener(f -> requestFuture.complete(request));
    }

    /** Saves any inbound error as the cause of the promise failure. */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      ChannelFuture unusedFuture =
          ctx.channel().closeFuture().addListener(f -> requestFuture.completeExceptionally(cause));
    }
  }

}
