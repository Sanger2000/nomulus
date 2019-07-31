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

import google.registry.monitoring.blackbox.metrics.MetricsCollector;
import google.registry.monitoring.blackbox.tokens.Token;
import google.registry.util.Clock;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.AbstractChannel;
import io.netty.channel.EventLoopGroup;

/**
 * Represents Sequence of {@link ProbingStep}s that the Prober performs in order.
 *
 *
 * <p>Created with {@link Builder} where we specify {@link EventLoopGroup}, {@link AbstractChannel} class type,
 * then sequentially add in the {@link ProbingStep.Builder}s in order and mark which one is the first repeated step.</p>
 *
 * <p>{@link ProbingSequence} implicitly points each {@link ProbingStep} to the next one, so once the first one
 * is activated with the requisite {@link Token}, the {@link ProbingStep}s do the rest of the work.</p>
 */
public class ProbingSequence {
  private ProbingStep firstStep;

  /**Each {@link ProbingSequence} requires a start token to begin running. */
  private Token startToken;

  public void start() {
    // calls the first step with startToken;
    firstStep.accept(startToken);
  }

  /**
   * Turns {@link ProbingStep.Builder}s into fully self-dependent sequence with
   * supplied {@link Bootstrap}.
   */
  public static class Builder {

    private ProbingStep currentStep;
    private ProbingStep firstStep;
    private ProbingStep firstRepeatedStep;

    private MetricsCollector metrics;
    private Clock clock;
    private Bootstrap bootstrap;
    private Token startToken;

    /**
     * Adds {@link MetricsCollector} that is supplied to each {@link ProbingStep}.
     *
     * <p>Must be called before adding {@link ProbingStep.Builder}s.</p>
     */
    public Builder setMetrics(MetricsCollector metrics) {
      this.metrics = metrics;
      return this;
    }

    /**
     * Adds {@link Clock} that is supplied to each {@link ProbingStep}.
     *
     * <p>Must be called before adding {@link ProbingStep.Builder}s.</p>
     */
    public Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Adds {@link Bootstrap} that is supplied to each {@link ProbingStep}.
     *
     * <p>Must be called before adding {@link ProbingStep.Builder}s.</p>
     */
    public Builder setBootstrap(Bootstrap bootstrap) {
      this.bootstrap = bootstrap;
      return this;
    }

    /** Adds start token that activate {@link ProbingSequence}. */
    public Builder addToken(Token token) {
      startToken = token;
      return this;
    }

    /**
     * Adds {@link ProbingStep.Builder}, which is supplied with {@link Bootstrap},
     * built, and pointed to by the previous {@link ProbingStep} added.
     */
    public Builder addStep(ProbingStep.Builder stepBuilder) {
      assert (bootstrap != null);
      ProbingStep step = stepBuilder
          .setMetrics(metrics)
          .setClock(clock)
          .setBootstrap(bootstrap)
          .build();

      if (currentStep == null)
        firstStep = step;
      else
        currentStep.nextStep(step);

      currentStep = step;
      return this;
    }

    /** We take special note of the first repeated step. */
    public Builder markFirstRepeated() {
      firstRepeatedStep = currentStep;
      return this;
    }

    /**
     * Points last {@link ProbingStep} to the {@code firstRepeatedStep} and
     * calls private constructor to create {@link ProbingSequence}.
     */
    public ProbingSequence build() {
      if (firstRepeatedStep == null)
        firstRepeatedStep = firstStep;

      currentStep.nextStep(firstRepeatedStep);
      currentStep.lastStep();
      return new ProbingSequence(this.firstStep, this.startToken);
    }
  }

  private ProbingSequence(ProbingStep firstStep, Token startToken) {
    this.firstStep = firstStep;
    this.startToken = startToken;
  }
}

