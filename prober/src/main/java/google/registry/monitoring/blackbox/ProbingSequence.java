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

import google.registry.monitoring.blackbox.tokens.Token;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.AbstractChannel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * Represents Sequence of {@link ProbingSteps} that the Prober performs in order
 *
 * @param <C> Primarily for testing purposes to specify channel type. Usually is {@link NioSocketChannel}
 * but for tests is {@link LocalChannel}
 *
 * <p>Created with {@link Builder} where we specify {@link EventLoopGroup}, {@link AbstractChannel} class type,
 * then sequentially add in the {@link ProbingStep}s in order and mark which one is the first repeated step.</p>
 *
 * <p>{@link ProbingSequence} implicitly points each {@link ProbingStep} to the next one, so once the first one
 * is activated with the requisite {@link Token}, the {@link ProbingStep}s do the rest of the work</p>
 */
public class ProbingSequence {
  private ProbingStep firstStep;

  /** A given {@link Prober} will run each of its {@link ProbingSequence}s with the same given {@link EventLoopGroup} */
  private EventLoopGroup eventGroup;

  /** Each {@link ProbingSequence} houses its own {@link Bootstrap} instance */
  private Bootstrap bootstrap;

  /**Each {@link ProbingSequence} requires a start token to begin running */
  private Token startToken;

  public Bootstrap getBootstrap() {
    return bootstrap;
  }

  public void start() {
    // calls the first step with input token;
    firstStep.accept(startToken);
  }

  /**
   * {@link Builder} which takes in {@link ProbingStep}s
   */
  public static class Builder {
    private ProbingStep currentStep;
    private ProbingStep firstStep;
    private ProbingStep firstSequenceStep;
    private EventLoopGroup eventLoopGroup;
    private Token startToken;

    public Builder eventLoopGroup(EventLoopGroup eventLoopGroup) {
      this.eventLoopGroup = eventLoopGroup;
      return this;
    }

    public Builder addStep(ProbingStep step) {
      if (currentStep == null) {
        firstStep = step;
      } else {
        currentStep.nextStep(step);
      }
      currentStep = step;
      return this;
    }
    public Builder addToken(Token token) {
      startToken = token;
      return this;
    }

    /** We take special note of the first repeated step and set pointers in {@link ProbingStep}s appropriately */
    public Builder makeFirstRepeated() {
      firstSequenceStep = currentStep;
      return this;
    }
    public ProbingSequence build() {
      currentStep.nextStep(firstSequenceStep);
      currentStep.lastStep();
      return new ProbingSequence(this.firstStep, this.currentStep, this.eventLoopGroup, this.startToken);
    }

  }

  /** We point each {@link ProbingStep} to the parent {@link ProbingSequence} so it can access its {@link Bootstrap} */
  private void setParents(ProbingStep lastStep) {
    ProbingStep currentStep = firstStep.parent(this);
    do {
      currentStep = currentStep.nextStep().parent(this);
    } while (currentStep != lastStep);

  }
  private ProbingSequence(ProbingStep firstStep, ProbingStep lastStep, EventLoopGroup eventLoopGroup, Token startToken) {
    this.firstStep = firstStep;
    this.eventGroup = eventLoopGroup;
    this.startToken = startToken;
    this.bootstrap = new Bootstrap()
        .group(eventGroup)
        .channel(NioSocketChannel.class);
    setParents(lastStep);
  }

  @Override
  public String toString() {
    return String.format("ProbingSequence with EventLoopGroup: %s", eventGroup);

  }
}

