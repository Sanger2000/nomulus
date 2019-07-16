package google.registry.monitoring.blackbox.TestServers;

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

public class WebWhoisServer extends TestServer {

  public WebWhoisServer(LocalAddress localAddress, ImmutableList<? extends ChannelHandler> handlers) {
    super(localAddress, handlers);
  }

  public WebWhoisServer(EventLoopGroup eventLoopGroup, LocalAddress localAddress, ImmutableList<? extends ChannelHandler> handlers) {
    super(eventLoopGroup, localAddress, handlers);
  }

  public static WebWhoisServer strippedServer(EventLoopGroup eventLoopGroup, LocalAddress localAddress, String redirectInput, String destinationInput) {
    return new WebWhoisServer(
        eventLoopGroup,
        localAddress,
        ImmutableList.of(new RedirectHandler(redirectInput, destinationInput))
    );
  }

  public static WebWhoisServer fullServer(EventLoopGroup eventLoopGroup, LocalAddress localAddress, String redirectInput, String destinationInput) {
    return new WebWhoisServer(
        eventLoopGroup,
        localAddress,
        ImmutableList.of(
            new HttpServerCodec(),
            new HttpObjectAggregator(1048576),
            new RedirectHandler(redirectInput, destinationInput))
    );
  }

  @Sharable
  static class RedirectHandler extends ChannelDuplexHandler {
    private String redirectInput;
    private String destinationInput;

    public RedirectHandler(String redirectInput, String destinationInput) {
      this.redirectInput = redirectInput;
      this.destinationInput = destinationInput;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      System.out.println(msg);
      HttpRequest request = (HttpRequest) msg;
      HttpResponse response;
      if (request.headers().get("host").equals(redirectInput)) {
        response = HttpResponseMessage.fromResponse(makeRedirectResponse(HttpResponseStatus.MOVED_PERMANENTLY, destinationInput, true, false));
      } else if (request.headers().get("host").equals(destinationInput)) {
        response = HttpResponseMessage.fromResponse(makeHttpResponse(HttpResponseStatus.OK));
      } else {
        response = HttpResponseMessage.fromResponse(makeHttpResponse(HttpResponseStatus.BAD_REQUEST));
      }
      ctx.channel().writeAndFlush(response);

    }
  }
}
