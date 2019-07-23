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

import google.registry.monitoring.blackbox.exceptions.ConnectionException;
import google.registry.util.Clock;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * {@link io.netty.channel.ChannelHandler} to be placed first in any channel pipeline for recording latency
 *
 * <p>Only exists one {@link Sharable} {@link TimerHandler}, which is used by each channel pipeline. It notes
 * which times to keep track of with a {@link HashMap} between channels and measured times</p>
 *
 * <p>When message passes outbound, it adds the timestamp to the queue of times associated with the
 * channel from which the message is passed through. When a response is received, it then records the
 * new timestamp and saves the latency of the channel as the difference between the first measured time
 * still in the queue for this channel and the current timestamp.</p>
 */
@Sharable
public class TimerHandler extends ChannelDuplexHandler {

  private Clock clock;

  /** Necessary for measuring time sent out */
  private final Map<Channel, Queue<Long>> requestSentTimeMap = new HashMap<>();

  /** Stores latencies for each channel until pulled out by {@link MetricsHandler} */
  private final Map<Channel, Long> latencyMap = new HashMap<>();

  public TimerHandler(Clock clock) {
    this.clock = clock;
  }

  /** Stores current timestamp in queue associated with current channel */
  private void recordTime(ChannelHandlerContext ctx) {
    if (!requestSentTimeMap.containsKey(ctx.channel())) {
      requestSentTimeMap.put(ctx.channel(), new ArrayDeque<>());
    }
    requestSentTimeMap.get(ctx.channel()).add(clock.nowUtc().getMillis());
  }

  /** In the event of a successful write, store the time. Otherwise throw {@link google.registry.monitoring.blackbox.exceptions.ConnectionException} */
  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
      ChannelFuture unusedFuture =
          ctx.write(msg, promise).addListener(
              future -> {
                if (future.isSuccess()) {
                    recordTime(ctx);
                } else {
                  throw new ConnectionException(future.cause());
                }
              }
          );
  }

  /** Store the latency associated with current channel */
  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (requestSentTimeMap.containsKey(ctx.channel())) {
      //only store latency if we have a recorded timestamp for this channel

      //store latency in the latencyMap with key as current channel
      latencyMap.put(ctx.channel(), clock.nowUtc().getMillis() - requestSentTimeMap.get(ctx.channel()).remove());

      //if the old queue is empty, remove it from the requestSentTimeMap
      if (requestSentTimeMap.get(ctx.channel()).isEmpty())
        requestSentTimeMap.remove(ctx.channel());
    }
    //after storing latency, pass on message inbound
    ctx.fireChannelRead(msg);
  }

  /** returns latency associated with channel if it exists, otherwise just null */
  public Long getLatency(Channel ch) {
    return (!latencyMap.containsKey(ch))? null : latencyMap.remove(ch);
  }
}
