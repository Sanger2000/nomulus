package google.registry.monitoring.blackbox.handlers;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

public class TimerHandler extends ChannelDuplexHandler {

  @Override
  public void flush(ChannelHandlerContext ctx) throws Exception {
    super.flush(ctx);
    //timer.start()
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    super.channelRead(ctx, msg);
    //timer.end()
  }

}
