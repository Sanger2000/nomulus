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

package google.registry.monitoring.blackbox.handlers;

import com.google.common.flogger.FluentLogger;
import google.registry.monitoring.blackbox.messages.InboundMessageType;
import google.registry.monitoring.blackbox.messages.OutboundMessageType;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 *Superclass of all {@link ChannelHandler}s placed at end of channel pipeline
 *
 * <p> {@code ActionHandler} inherits from {@link SimpleChannelInboundHandler< InboundMessageType >}, as it should only be passed in
 * messages that implement the {@link InboundMessageType} interface. </p>
 *
 * <p> The {@code ActionHandler} skeleton exists for a few main purposes. First, it returns a {@link ChannelPromise},
 * which informs the {@link ProbingAction} in charge that a response has been read. Second, it stores the {@link OutboundMessageType}
 * passed down the pipeline, so that subclasses can use that information for their own processes. Lastly, with any exception
 * thrown, the connection is closed, and the ProbingAction governing this channel is informed of the error. Subclasses
 * specify further work to be done for specific kinds of channel pipelines. </p>
 */
public abstract class ActionHandler extends SimpleChannelInboundHandler<InboundMessageType> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected ChannelPromise finished;

  /** Takes in {@link OutboundMessageType} type and saves for subclasses. Then returns initialized {@link ChannelPromise}*/
  public ChannelFuture getFuture() {
    return finished;
  }

  /** Initializes new {@link ChannelPromise} */
  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    //Once handler is added to channel pipeline, initialize channel and future for this handler
    finished = ctx.newPromise();
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, InboundMessageType inboundMessage) throws Exception {
    //simply marks finished as success
    finished.setSuccess();
  }

  /** Logs the channel and pipeline that caused error, closes channel, then informs {@link ProbingAction} listeners of error */
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    logger.atSevere().withCause(cause).log(String.format(
        "Attempted Action was unsuccessful with channel: %s, having pipeline: %s",
        ctx.channel().toString(),
        ctx.channel().pipeline().toString()));

    finished.setFailure(cause);
    ChannelFuture closedFuture = ctx.channel().close();
    closedFuture.addListener(f -> logger.atInfo().log("Unsuccessful channel connection closed"));
  }
}

