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

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
import google.registry.monitoring.blackbox.connection.ProbingAction;
import google.registry.monitoring.blackbox.connection.Protocol;
import google.registry.monitoring.blackbox.exceptions.FailureException;
import google.registry.monitoring.blackbox.exceptions.UndeterminedStateException;
import google.registry.monitoring.blackbox.handlers.ActionHandler.ResponseType;
import google.registry.monitoring.blackbox.metrics.MetricsCollector;
import google.registry.monitoring.blackbox.tokens.Token;
import google.registry.monitoring.blackbox.messages.OutboundMessageType;
import google.registry.util.Clock;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import java.net.SocketAddress;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.joda.time.Duration;

/**
 * {@link AutoValue} class that represents generator of actions performed at each step
 * in {@link ProbingSequence}.
 *
 * <p>Holds the unchanged components in a given step of the {@link ProbingSequence}, which are
 * the {@link OutboundMessageType}, {@link Protocol}, {@link Duration}, and {@link Bootstrap} instances.
 * It then modifies these components on each loop iteration with the consumed {@link Token} and from that,
 * generates a new {@link ProbingAction} to call.</p>
 *
 */
@AutoValue
public abstract class ProbingStep implements Consumer<Token> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Necessary boolean to inform when to obtain next {@link Token}*/
  protected boolean isLastStep = false;
  private ProbingStep nextStep;

  /** Time delay duration between actions. */
  abstract Duration duration();

  /** {@link Protocol} type for this step. */
  abstract Protocol protocol();

  /** {@link OutboundMessageType} instance that serves as template to be modified by {@link Token}. */
  abstract OutboundMessageType messageTemplate();

  /** {@link Bootstrap} instance provided by parent {@link ProbingSequence} that allows for creation of new channels. */
  abstract Bootstrap bootstrap();

  /** {@link MetricsCollector} instance provided by parent {@link ProbingSequence} that allows for writing collected metrics. */
  abstract MetricsCollector metrics();

  /** {@link Clock} instance provided by parent {@link ProbingSequence} that records times for latency. */
  abstract Clock clock();

  /**
   * Specifies {@link SocketAddress} for {@link Bootstrap} to bind to when forming new channel. Mainly used when
   * specifying {@link io.netty.channel.local.LocalAddress} for testing.
   */
  @Nullable abstract SocketAddress address();


  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder setDuration(Duration value);

    public abstract Builder setProtocol(Protocol value);

    public abstract Builder setMessageTemplate(OutboundMessageType value);

    public abstract Builder setBootstrap(Bootstrap value);

    public abstract Builder setMetrics(MetricsCollector metrics);

    public abstract Builder setClock(Clock clock);

    public abstract Builder setAddress(SocketAddress address);

    public abstract ProbingStep build();
  }

  public static Builder builder() {
    return new AutoValue_ProbingStep.Builder();
  }

  void lastStep() {
    isLastStep = true;
  }

  void nextStep(ProbingStep step) {
    this.nextStep = step;
  }

  ProbingStep nextStep() {
    return this.nextStep;
  }

  /** Generates a new {@link ProbingAction} from {@code token} modified {@link OutboundMessageType} */
  private ProbingAction generateAction(Token token) throws UndeterminedStateException {
    OutboundMessageType message = token.modifyMessage(messageTemplate());
    ProbingAction.Builder probingActionBuilder = ProbingAction.builder()
        .setDelay(duration())
        .setProtocol(protocol())
        .setOutboundMessage(message)
        .setHost(token.getHost())
        .setBootstrap(bootstrap())
        .setAddress(address());

    if (token.channel() != null)
      probingActionBuilder.setChannel(token.channel());

    return probingActionBuilder.build();
  }


  /** On the last step, gets the next {@link Token}. Otherwise, uses the same one. */
  private Token generateNextToken(Token token) {
    return (isLastStep) ? token.next() : token;
  }

  /** Records latency and status to {@link MetricsCollector}. */
  private void recordMetrics(long start, ResponseType status) {
    long latency = clock().nowUtc().getMillis() - start;
    metrics().recordResult(protocol().name(), messageTemplate().name(), status, latency);
  }

  /**
   * Generates new {@link ProbingAction}, calls the action, then records the result of the action.
   *
   * @param token - used to generate the {@link ProbingAction} by calling {@code generateAction}.
   *
   * <p>If unable to generate the action, or the calling the action results in an immediate error,
   * we record the result as an ERROR. Otherwise, if the future marked as finished when the action is
   * completed is marked as a success, we record the result as SUCCESS. Otherwise, if the cause of failure
   * is a {@link FailureException}, we record it as a FAILURE. If neither of those are the case, we record
   * the result as an ERROR. </p>
   */
  @Override
  public void accept(Token token) {
    ProbingAction currentAction;
    long start = clock().nowUtc().getMillis();
    //attempt to generate new action. On error, move on to next step
    try {
      currentAction = generateAction(token);
    } catch(UndeterminedStateException e) {
      logger.atWarning().withCause(e).log("Error in Action Generation");

      recordMetrics(start, ResponseType.ERROR);

      nextStep.accept(generateNextToken(token));
      return;
    }


    ChannelFuture future;
    try {
      //call the generated action
      future = currentAction.call();
    } catch(UndeterminedStateException e) {
      //On error in calling action, log error and record metrics as ERROR
      logger.atWarning().withCause(e).log("Error in Action Performed");
      recordMetrics(start, ResponseType.ERROR);

      //Move on to next step in ProbingSequence
      nextStep.accept(generateNextToken(token));
      return;
    }


    future.addListener(f -> {
      if (f.isSuccess()) {
        //On a successful result, we log as a successful step, and record as a SUCCESS
        logger.atInfo().log(String.format("Successfully completed Probing Step: %s", this));
        recordMetrics(start, ResponseType.SUCCESS);
      } else {
        //On a failed result, we log the failure
        logger.atSevere().withCause(f.cause()).log("Did not result in future success");

        if (f.cause() instanceof FailureException)
          //On an instance of a FailureException, record metrics as FAILURE
          recordMetrics(start, ResponseType.FAILURE);
        else
          //Otherwise, record metrics as ERROR
          recordMetrics(start, ResponseType.ERROR);
      }

      if (protocol().persistentConnection())
        //If the connection is persistent, we store the channel in the token
        token.setChannel(currentAction.channel());

      //Move on the the next step in the ProbingSequence
      nextStep.accept(generateNextToken(token));


    });
  }

  @Override
  public String toString() {
    return String.format("ProbingStep with Protocol: %s\n" +
        "OutboundMessage: %s\n",
        protocol(),
        messageTemplate().getClass().getName());
  }

}


