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

import static google.registry.monitoring.blackbox.ProbingAction.PROBING_ACTION_KEY;
import static google.registry.monitoring.blackbox.Protocol.PROTOCOL_KEY;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import google.registry.monitoring.blackbox.NewChannelAction;
import google.registry.monitoring.blackbox.ProbingAction;
import google.registry.monitoring.blackbox.Prober;
import google.registry.monitoring.blackbox.Protocol;
import google.registry.monitoring.blackbox.messages.HttpRequestMessage;
import google.registry.monitoring.blackbox.messages.HttpResponseMessage;
import google.registry.monitoring.blackbox.messages.InboundMarker;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URL;
import javax.inject.Inject;
import org.joda.time.Duration;

public class WebWhoisActionHandler extends ActionHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject
  public WebWhoisActionHandler() {}

  /** Method needed for workaround in order to create ProbingAction builder with Channel type specified by the current channel type */
  private <C extends AbstractChannel> NewChannelAction.Builder<C> createBuilder(
      Class<? extends Channel> className, ProbingAction currentAction) {
    return ((NewChannelAction<C>) currentAction).toBuilder();
  }


  @Override
  public void channelRead0(ChannelHandlerContext ctx, InboundMarker msg) throws Exception {

    HttpResponseMessage response = (HttpResponseMessage) msg;


    if (response.status() == HttpResponseStatus.OK) {
      logger.atInfo().log("Received Successful HttpResponseStatus");

      HttpRequestMessage request = (HttpRequestMessage) outboundMessage;

      //warns that we have not discarded the outbound message, and proceeds to discard it
      if (request.refCnt() != 0) {
        logger.atWarning().log("outboundMessage is still being stored in memory");
        request.release(request.refCnt());
      }

      finished.setSuccess();

      System.out.println(response);

    } else if (response.status() == HttpResponseStatus.FOUND || response.status() == HttpResponseStatus.MOVED_PERMANENTLY) {

      //Obtain url to be redirected to
      URL url = new URL(response.headers().get("Location"));

      //From url, extract new host, port, and path
      String newHost = url.getHost();
      String newPath = url.getPath();
      int newPort = url.getDefaultPort();

      logger.atInfo().log(String.format("Redirected to %s with host: %s, port: %d, and path: %s", url, newHost, newPort, newPath));

      //Construct new Protocol to reflect redirected host, path, and port
      Protocol newProtocol = Prober.portToProtocolMap.get(newPort);

      ProbingAction oldAction = ctx.channel().attr(PROBING_ACTION_KEY).get();

      //Modify HttpRequest sent to remote host to reflect new path and host
      HttpRequestMessage httpRequest = ((HttpRequestMessage)oldAction.outboundMessage()).setUri(newPath);
      httpRequest.headers().set(HttpHeaderNames.HOST, newHost);

      //Create new probingAction that takes in the new Protocol and HttpRequest message
      ProbingAction redirectedAction = createBuilder(ctx.channel().getClass(), oldAction)
          .protocol(newProtocol)
          .outboundMessage(httpRequest)
          .delay(Duration.ZERO)
          .host(newHost)
          .path(newPath)
          .build();

      //Mainly for testing, to check the probing action was created appropriately
      ctx.channel().attr(PROBING_ACTION_KEY).set(redirectedAction);

      //close this channel as we no longer need it
      ChannelFuture future = ctx.close();
      future.addListener(
          f -> {
            logger.atInfo().log("Successfully Closed Connection");

            //Once channel is closed, establish new connection to redirected host, and repeat same actions
            ChannelFuture secondFuture = redirectedAction.call();

            //Once we have a successful call, set original ChannelPromise as success to tell ProbingStep we can move on
            secondFuture.addListener(f2 -> finished.setSuccess());

          }
      );
    } else {
      finished.setFailure(new RuntimeException());
      logger.atWarning().log(String.format("Received unexpected response: %s", response.status()));

    }
  }


}

