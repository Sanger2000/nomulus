package google.registry.monitoring.blackbox.handlers;

import static google.registry.monitoring.blackbox.connection.ProbingAction.PROBING_ACTION_KEY;

import google.registry.monitoring.blackbox.connection.ProbingAction;
import google.registry.monitoring.blackbox.handlers.ActionHandler.ResponseType;
import google.registry.monitoring.blackbox.metrics.MetricsCollector;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Last {@link ChannelHandler} in any pipeline that informs {@link MetricsCollector} the
 * status of the action performed and its latency.
 *
 * <p>Requires the {@link TimerHandler} to be passed in to it, in order to obtain latency
 * associated with current action in current channel</p>
 */
public class MetricsHandler extends SimpleChannelInboundHandler<ResponseType> {

  TimerHandler timer;
  MetricsCollector metrics;

  public MetricsHandler(TimerHandler timer, MetricsCollector metrics) {
    this.timer = timer;
    this.metrics = metrics;
  }

  /**
   * Obtains latency from {@link TimerHandler}. If it isn't null, we pass recorded
   * metrics to the {@link MetricsCollector}, which requires the {@code protocolName},
   * {@code actionName}, {@link ResponseType}, and {@code latency}.
   */
  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ResponseType msg) {
    //obtains latency from timer
    Long latency = timer.getLatency(ctx.channel());

    if (latency != null) {
      ProbingAction action = ctx.channel().attr(PROBING_ACTION_KEY).get();

      //two key attributes that identify specific type of query we are gathering metrics on
      String protocolName = action.protocol().name();
      String actionName = action.name();

      //stores results in MetricsCollector
      metrics.recordResult(protocolName, actionName, msg, latency);
    }

  }
}
