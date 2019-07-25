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

import static google.registry.monitoring.blackbox.TestUtils.LOCALHOST;
import static google.registry.monitoring.blackbox.TestUtils.makeHttpResponse;
import static google.registry.monitoring.blackbox.TestUtils.makeRedirectResponse;

import com.google.common.collect.ImmutableList;
import google.registry.monitoring.blackbox.messages.HttpResponseMessage;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * {@link TestServer} subtype that performs WebWhois Services Expected
 *
 * <p>It will either redirect the client to the correct location if given the
 * requisite redirect input, give the client a successful success if they give
 * the expected final destination, or give the client an error message if given
 * an unexpected host location</p>
 */
public class WebWhoisServer extends TestServer {

  private WebWhoisServer() {
    super();
  }
  private WebWhoisServer(EventLoopGroup eventLoopGroup) {
    super(eventLoopGroup);
  }

  @Override
  protected void setupServer(int port, ImmutableList<? extends ChannelHandler> handlers) {
    super.setupServer(port, handlers);
  }

  /** Creates server that doesn't deal with {@link ByteBuf} conversion and just sends the HttpRequestMessage object through pipeline */
  public static WebWhoisServer strippedServer(EventLoopGroup eventLoopGroup, int port, String redirectInput, String destinationInput) {
    WebWhoisServer strippedServer = new WebWhoisServer(eventLoopGroup);
    strippedServer.setupServer(port, ImmutableList.of(new RedirectHandler(redirectInput, destinationInput)));
    return strippedServer;
  }
  /** Creates server that sends exactly what we expect a remote server to send as a success, by sending the {@link ByteBuf} of the success through pipeline */
  public static WebWhoisServer fullServer(EventLoopGroup eventLoopGroup, int port, String redirectInput, String destinationInput) {
    WebWhoisServer strippedServer = new WebWhoisServer(eventLoopGroup);
    strippedServer.setupServer(
        port,
        ImmutableList.of(
            new HttpServerCodec(),
            new HttpObjectAggregator(1048576),
            new RedirectHandler(redirectInput, destinationInput)
        )
    );

    return strippedServer;
  }

  /**
   * Handler that will wither redirect client, give successful success, or give error messge
   */
  @Sharable
  static class RedirectHandler extends ChannelDuplexHandler {
    private String redirectInput;
    private String destinationInput;

    /**
     *
     * @param redirectInput - Server will send back redirect to {@code destinationInput} when receiving a request with this host location
     * @param destinationInput - Server will send back an {@link HttpResponseStatus.OK} success when receiving a request with this host location
     */
    public RedirectHandler(String redirectInput, String destinationInput) {
      this.redirectInput = redirectInput;
      this.destinationInput = destinationInput;
    }

    /** Reads input {@link HttpRequest}, and creates appropriate {@link HttpResponseMessage} based on what header location is */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      System.out.println("test1");
      HttpRequest request = (HttpRequest) msg;
      HttpResponse response;
      if (request.uri().equals(redirectInput)) {
        response = HttpResponseMessage.fromResponse(makeRedirectResponse(HttpResponseStatus.MOVED_PERMANENTLY, LOCALHOST + "/" + destinationInput, true, false));
      } else if (request.uri().equals(destinationInput)) {
        response = HttpResponseMessage.fromResponse(makeHttpResponse(HttpResponseStatus.OK));
      } else {
        response = HttpResponseMessage.fromResponse(makeHttpResponse(HttpResponseStatus.BAD_REQUEST));
      }
      ctx.channel().writeAndFlush(response);

    }
  }
}
