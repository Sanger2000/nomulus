package google.registry.monitoring.blackbox.handlers;

import google.registry.monitoring.blackbox.exceptions.ResponseException;
import google.registry.monitoring.blackbox.messages.EppResponseMessage;
import google.registry.monitoring.blackbox.messages.InboundMessageType;
import io.netty.channel.ChannelHandlerContext;
import javax.inject.Inject;

public class EppActionHandler extends ActionHandler {
  @Inject
  public EppActionHandler() {}

  @Override
  public void channelRead0(ChannelHandlerContext ctx, InboundMessageType msg) throws ResponseException {
    EppResponseMessage response = (EppResponseMessage) msg;
    response.decode();
    super.channelRead0(ctx, msg);
  }


}
