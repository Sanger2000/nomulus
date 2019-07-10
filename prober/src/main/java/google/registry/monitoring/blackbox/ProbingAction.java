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

package google.registry.monitoring.blackbox;

import static com.google.common.flogger.StackSize.SMALL;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import google.registry.monitoring.blackbox.handlers.ActionHandler;
import google.registry.monitoring.blackbox.messages.OutboundMarker;
import io.netty.util.AttributeKey;
import java.util.concurrent.TimeUnit;
import org.joda.time.Duration;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import java.util.concurrent.Callable;
import javax.inject.Provider;

/**
 *Class that represents given action in sequence of probing
 *
 */


public abstract class ProbingAction implements Callable<ChannelFuture> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Attribute Key that links channel to its {@link ProbingAction}*/
  public final static AttributeKey<ProbingAction> PROBING_ACTION_KEY = AttributeKey.valueOf("PROBING_ACTION_KEY");


  /** {@link ActionHandler} Associated with this {@link ProbingAction}*/
  private ActionHandler actionHandler;


  /**
   * The requisite instance of {@link ActionHandler}, which is always the last {@link ChannelHandler} in the pipeline
   */
  private ActionHandler actionHandler() {
    return actionHandler;
  }


  /** {@link Timer} that rate limits probing*/
  private static final Timer timer = new HashedWheelTimer();


  /** actual {@link Duration} of this delay*/
  public abstract Duration delay();

  /** message to send to server */
  public abstract OutboundMarker outboundMessage();

  /**
   * @return {@link Channel} object that represents connection between prober client and server
   */
  public abstract Channel channel();

  /**
   * @return The {@link Protocol} instance that represents action to be tested by this part in sequences
   */
  public abstract Protocol protocol();

  public abstract String host();

  public abstract String path();

  /**
   *
   * @return {@link Builder} that lets us build a new ProbingAction by customizing abstract methods
   */
  public abstract <B extends Builder<B, P>, P extends ProbingAction> Builder<B, P> toBuilder();


  /** Performs the action specified by the ProbingAction and sets the ChannelPromise specified to a success */
  private void informListeners(ChannelPromise finished) {
    ChannelFuture channelFuture = actionHandler().getFuture(outboundMessage());
    channel().writeAndFlush(outboundMessage());
    channelFuture.addListeners(
        future -> finished.setSuccess(),
        future -> {
          if (!protocol().persistentConnection()) {

            //If we created a new channel for this action, close the connection to the channel
            ChannelFuture closedFuture = channel().close();
            closedFuture.addListener(
                f -> {
                  if (f.isSuccess())
                    logger.atInfo().log("Closed stale channel. Moving on to next ProbingStep");
                  else
                    logger.atWarning()
                        .log("Could not close channel. Stale connection still exists.");
                }
            );
          }
        }
    );
  }
  /**
   * The method that calls the {@link ActionHandler} to send a message down the channel pipeline
   * @return future that denotes when the action has been successfully performed
   */

  @Override
  public ChannelFuture call() {

    //Sets Action Handler to appropriately the last channel in the pipeline
    //Logs severe if the last channel in the pipeline is not
    try {
      this.actionHandler = (ActionHandler) this.channel().pipeline().last();
    } catch (ClassCastException exception) {
      logger.atSevere().withStackTrace(SMALL).log("Last Handler in the ChannelPipeline is not an ActionHandler");
    }

    //ChannelPromise that we use to inform ProbingStep when we are finished.
    ChannelPromise finished = channel().newPromise();

    //Every specified time frame by delay(), we perform the next action in our sequence and inform ProbingStep when finished
    if (!delay().equals(Duration.ZERO)) {
      timer.newTimeout(timeout -> {
            // Write appropriate message to pipeline
            informListeners(finished);
          },
          delay().getStandardSeconds(),
          TimeUnit.SECONDS);
    } else {
      //if no delay, just perform the next action, and inform ProbingStep when finished
      informListeners(finished);
    }

    return finished;
  }

  public abstract static class Builder<B extends Builder<B, P>, P extends ProbingAction> {

    public abstract B delay(Duration value);

    public abstract B outboundMessage(OutboundMarker value);

    public abstract B protocol(Protocol value);

    public abstract B host(String value);

    public abstract B path(String value);

    public abstract P build();

  }

  /**
   * @param channelPipeline is pipeline associated with channel that we want to add handlers to
   * @param handlerProviders are a list of provider objects that give us the requisite handlers Adds
   * to the pipeline, the list of handlers in the order specified
   */
  static void addHandlers(
      ChannelPipeline channelPipeline,
      ImmutableList<Provider<? extends ChannelHandler>> handlerProviders) {
    for (Provider<? extends ChannelHandler> handlerProvider : handlerProviders) {
      channelPipeline.addLast(handlerProvider.get());
    }
  }
}

