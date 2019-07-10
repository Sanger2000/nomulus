package google.registry.monitoring.blackbox.handlers;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import google.registry.monitoring.blackbox.TestUtils.DuplexMessageTest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class ConversionHandler extends ChannelDuplexHandler {

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    ByteBuf buf = (ByteBuf) msg;
    super.channelRead(ctx, new DuplexMessageTest(buf.toString(UTF_8)));
    buf.release();
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
      throws Exception {
    String message = msg.toString();
    ByteBuf buf = Unpooled.wrappedBuffer(message.getBytes(US_ASCII));
    super.write(ctx, buf, promise);
  }
}

