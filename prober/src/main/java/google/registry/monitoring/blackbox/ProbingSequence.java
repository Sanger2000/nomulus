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

import google.registry.monitoring.blackbox.ProberModule.ProberComponent;
import google.registry.monitoring.blackbox.Tokens.Token;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.AbstractChannel;
import io.netty.channel.EventLoopGroup;

class ProbingSequence<C extends AbstractChannel> {
  private ProbingStep<C> firstStep;
  private EventLoopGroup eventGroup;
  private Bootstrap bootstrap;

  public Bootstrap getBootstrap() {
    return bootstrap;
  }

  public void start(ProberComponent proberComponent) {
    // create a new unique token;
    Token token = proberComponent.provideToken();
    firstStep.accept(token);
  }

  /**
   * Builder that sequentially adds steps
   */
  static class Builder<C extends AbstractChannel> {
    private ProbingStep<C> currentStep;
    private ProbingStep<C> firstStep;
    private ProbingStep<C> firstSequenceStep;
    private EventLoopGroup eventLoopGroup;
    private Class<C> classType;

    Builder<C> eventLoopGroup(EventLoopGroup eventLoopGroup) {
      this.eventLoopGroup = eventLoopGroup;
      return this;
    }

    Builder<C> addStep(ProbingStep<C> step) {
      if (currentStep == null) {
        firstStep = step;
      } else {
        currentStep.nextStep(step);
      }

      currentStep = step;
      return this;

    }
    Builder<C> makeFirstRepeated() {
      firstSequenceStep = currentStep;
      return this;
    }
    public Builder<C> setClass(Class<C> classType) {
      this.classType = classType;
      return this;
    }

    public ProbingSequence<C> build() {
      currentStep.nextStep(firstSequenceStep);
      currentStep.lastStep();
      return new ProbingSequence<>(this.firstStep, this.eventLoopGroup, this.classType);
    }

  }

  private void setParents() {
    ProbingStep<C> currentStep = firstStep.parent(this).nextStep();

    while (currentStep != firstStep) {
      currentStep = currentStep.parent(this).nextStep();
    }
  }
  private ProbingSequence(ProbingStep<C> firstStep, EventLoopGroup eventLoopGroup, Class<C> classType) {
    this.firstStep = firstStep;
    this.eventGroup = eventLoopGroup;
    this.bootstrap = new Bootstrap()
        .group(eventGroup)
        .channel(classType);
    setParents();
  }
}

