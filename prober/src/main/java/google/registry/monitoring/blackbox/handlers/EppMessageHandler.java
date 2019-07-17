package google.registry.monitoring.blackbox.handlers;

import google.registry.monitoring.blackbox.messages.EppMessage;
import google.registry.monitoring.blackbox.messages.EppRequestMessage;
import google.registry.monitoring.blackbox.messages.EppResponseMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import javax.inject.Inject;

public class EppMessageHandler extends MessageHandler {
  private String clTRID;
  private EppResponseMessage response;

  @Inject
  public EppMessageHandler(EppResponseMessage msg) {
    this.response = msg;
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
      throws Exception {
    EppRequestMessage request = (EppRequestMessage) msg;

    super.write(ctx, request.bytes(), promise);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg)
      throws Exception {
    ByteBuf buf = (ByteBuf) msg;
    response.getDocument(clTRID, buf);
    super.channelRead(ctx, response);
  }
}
