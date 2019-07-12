package google.registry.monitoring.blackbox.handlers;

import google.registry.monitoring.blackbox.messages.EppRequestMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import javax.inject.Inject;

public class EppMessageHandler extends MessageHandler {

  @Inject
  public EppMessageHandler() {}

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    EppRequestMessage request = (EppRequestMessage) msg;
    super.write(ctx, request.bytes(), promise);
  }
}
