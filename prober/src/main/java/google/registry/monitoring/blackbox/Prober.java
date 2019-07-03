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

import com.google.common.collect.ImmutableMap;
import google.registry.monitoring.blackbox.ProberModule.ProberComponent;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class Prober {

  private static ProberComponent proberComponent = DaggerProberModule_ProberComponent.builder().build();
  public static final ImmutableMap<Integer, Protocol> portToProtocolMap = proberComponent.providePortToProtocolMap();

  private static final EventLoopGroup eventGroup = new NioEventLoopGroup();

  public static void main(String[] args) {

    ProbingSequence<NioSocketChannel> sequence = new ProbingSequence.Builder<NioSocketChannel>()
        .eventLoopGroup(eventGroup)
        .addStep(new ProbingStepWeb<>(portToProtocolMap.get(80)))
        .makeFirstRepeated()
        .setClass(NioSocketChannel.class)
        .build();

    sequence.start(Prober.proberComponent);


  }
}
