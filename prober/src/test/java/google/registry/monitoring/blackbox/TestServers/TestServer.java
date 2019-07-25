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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.monitoring.blackbox.TestUtils.LOCALHOST;
import static google.registry.monitoring.blackbox.connection.ProbingAction.PROBING_ACTION_KEY;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import google.registry.monitoring.blackbox.connection.ProbingAction;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
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
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.junit.rules.ExternalResource;

/**
 * Mock Server Superclass whose subclasses implement specific behaviors we expect blackbox server to perform
 */
public abstract class TestServer extends ExternalResource {

  protected EventLoopGroup eventLoopGroup;
  protected Channel channel;

  protected TestServer() {
    eventLoopGroup = new NioEventLoopGroup(1);
  }

  protected TestServer(EventLoopGroup eventLoopGroup) {
    this.eventLoopGroup = eventLoopGroup;
  }

  protected void setupServer(int port, ImmutableList<? extends ChannelHandler> handlers) {

    //Creates ChannelInitializer with handlers specified
    ChannelInitializer<NioSocketChannel> serverInitializer = new ChannelInitializer<NioSocketChannel>() {
      @Override
      protected void initChannel(NioSocketChannel ch) {
        for (ChannelHandler handler : handlers) {
          ch.pipeline().addLast(handler);
        }
      }
    };
    //Sets up serverBootstrap with specified initializer, eventLoopGroup, and using LocalServerChannel class
    ServerBootstrap serverBootstrap =
        new ServerBootstrap()
            .group(eventLoopGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(serverInitializer);

    try {

      ChannelFuture future = serverBootstrap.bind(port).sync();

    } catch (InterruptedException e) {
      throw new ExceptionInInitializerError(e);

    }
  }

  /** Sets up a client channel connecting to the give local address. */
  protected void setUpClient(
      int port,
      ProbingAction probingAction,
      ImmutableList<? extends ChannelHandler> handlers) {

    ChannelInitializer<NioSocketChannel> clientInitializer =
        new ChannelInitializer<NioSocketChannel>() {
          @Override
          protected void initChannel(NioSocketChannel ch) throws Exception {
            // Add the given handler
            for (ChannelHandler handler : handlers)
              ch.pipeline().addLast(handler);
          }
        };
    Bootstrap b =
        new Bootstrap()
            .group(eventLoopGroup)
            .channel(NioSocketChannel.class)
            .handler(clientInitializer)
            .attr(PROBING_ACTION_KEY, probingAction);


    channel = b.connect(LOCALHOST, port).syncUninterruptibly().channel();
  }

  public Channel getChannel() {
    checkReady();
    return channel;
  }

  protected void checkReady() {
    checkState(channel != null, "Must call setUpClient to finish NettyRule setup");
  }








}
