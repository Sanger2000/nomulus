// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

import com.google.common.flogger.FluentLogger;
import google.registry.monitoring.blackbox.Tokens.Token;
import google.registry.monitoring.blackbox.messages.OutboundMarker;
import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelFuture;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import java.util.function.Consumer;
import org.joda.time.Duration;


public abstract class ProbingStep<C extends AbstractChannel> implements Consumer<Token> {


  protected static final Duration DEFAULT_DURATION = new Duration(2000L);
  private static final Timer timer = new HashedWheelTimer();
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private boolean isLastStep = false;
  private ProbingStep<C> nextStep;
  private ProbingSequence<C> parent;

  protected Protocol protocol;
  protected Duration duration;

  protected abstract OutboundMarker message();

  void lastStep() {
    isLastStep = true;
  }

  void nextStep(ProbingStep<C> step) {
    this.nextStep = step;
  }

  ProbingStep<C> nextStep() {
    return this.nextStep;
  }

  ProbingStep<C> parent(ProbingSequence<C> parent) {
    this.parent = parent;
    return this;
  }

  private ProbingAction generateAction(Token token) {
    ProbingAction generatedAction;

    OutboundMarker message = token.modifyMessage(message());

    if (protocol.persistentConnection()) {
      generatedAction = ExistingChannelAction.builder()
          .delay(duration)
          .protocol(protocol)
          .outboundMessage(message)
          .host(token.getHost())
          .channel(token.channel())
          .build();
    } else {
      generatedAction = NewChannelAction.<C>builder()
          .delay(duration)
          .protocol(protocol)
          .outboundMessage(message)
          .host(token.getHost())
          .bootstrap(parent.getBootstrap())
          .build();

    }
    return generatedAction;
  }


  private Token generateNextToken(Token token) {
    return (isLastStep) ? token.next() : token;
  }

  @Override
  public void accept(Token token) {
    ChannelFuture future;
    future = generateAction(token).call();

    future.addListener(f -> {
      if (f.isSuccess()) {
        logger.atInfo().log(String.format("Successfully completed Probing Step: %s", this));
        nextStep.accept(generateNextToken(token));
      } else {
        logger.atSevere().log("Did not result in future success");
      }
    });
  }



}


