package google.registry.monitoring.blackbox.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import javax.inject.Inject;

public class ResponseDowncastHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

  @Inject
  public ResponseDowncastHandler() {}

  @Override
  public void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
    ctx.fireChannelRead(msg);
  }
}
