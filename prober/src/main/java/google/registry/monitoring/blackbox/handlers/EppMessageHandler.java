package google.registry.monitoring.blackbox.handlers;

import google.registry.monitoring.blackbox.messages.EppClientException;
import google.registry.monitoring.blackbox.messages.EppMessage;
import google.registry.monitoring.blackbox.messages.EppRequestMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import javax.inject.Inject;

public class EppMessageHandler extends MessageHandler {

  @Inject
  public EppMessageHandler() {}

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
    int capacity = buf.readInt() - EppMessage.HEADER_LENGTH;


  }
}
