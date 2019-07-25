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

import static google.registry.monitoring.blackbox.ProberConfig.getProberConfig;

import com.beust.jcommander.Parameter;
import com.google.api.services.cloudkms.v1.CloudKMS;
import com.google.api.services.cloudkms.v1.model.DecryptRequest;
import com.google.api.services.storage.Storage;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import google.registry.monitoring.blackbox.ProberConfig.Environment;
import google.registry.monitoring.blackbox.handlers.MetricsHandler;
import google.registry.monitoring.blackbox.handlers.TimerHandler;
import google.registry.monitoring.blackbox.metrics.MetricsCollector;
import google.registry.monitoring.blackbox.modules.CertificateModule;
import google.registry.monitoring.blackbox.modules.EppModule.EppProtocol;
import google.registry.monitoring.blackbox.tokens.Token;
import google.registry.monitoring.blackbox.connection.Protocol;
import google.registry.monitoring.blackbox.modules.EppModule;
import google.registry.monitoring.blackbox.modules.TokenModule;
import google.registry.monitoring.blackbox.modules.WebWhoisModule;
import google.registry.monitoring.blackbox.modules.WebWhoisModule.HttpWhoisProtocol;
import google.registry.monitoring.blackbox.modules.WebWhoisModule.HttpsWhoisProtocol;
import google.registry.monitoring.blackbox.modules.WebWhoisModule.WebWhoisProtocol;
import google.registry.util.Clock;
import google.registry.util.SystemClock;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.Set;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * {@link Dagger} main module, which Provides {@link ProbingSequences} and houses {@link ProberComponent}
 *
 * <p>Provides each {@link ProbingSequence}, each {@link Token} and basic building blocks that
 * comprise the entire Prober. In addition, other minor necessary global variables are provided,
 * such as the {@code portToProtocolMap}</p>
 */
@Module
public class ProberModule {
  private final int httpWhoIsPort = 80;
  private final int httpsWhoIsPort = 443;
  private final int eppPort = 700;

  @Parameter(names = "--env", description = "Environment to run the proxy in")
  private Environment env = Environment.LOCAL;


  @Provides
  @Singleton
  EventLoopGroup provideEventLoopGroup() {
    return new NioEventLoopGroup();
  }

  @Provides
  @HttpWhoisProtocol
  ProbingSequence provideHttpWhoisSequence(
      @HttpWhoisProtocol ProbingStep probingStep,
      @WebWhoisProtocol Token token,
      EventLoopGroup eventLoopGroup) {
    return new ProbingSequence.Builder()
        .addToken(token)
        .addStep(probingStep)
        .makeFirstRepeated()
        .eventLoopGroup(eventLoopGroup)
        .build();
  }

  @Provides
  @HttpsWhoisProtocol
  ProbingSequence provideHttpsWhoisSequence(
      @HttpsWhoisProtocol ProbingStep probingStep,
      @WebWhoisProtocol Token token,
      EventLoopGroup eventLoopGroup) {
    return new ProbingSequence.Builder()
        .addToken(token)
        .addStep(probingStep)
        .makeFirstRepeated()
        .eventLoopGroup(eventLoopGroup)
        .build();
  }

  @Provides
  @Named("Epp-Basic")
  ProbingSequence provideBasicEppSequence(
      @Named("Hello") ProbingStep helloStep,
      @Named("Login") ProbingStep loginStep,
      @Named("Logout") ProbingStep logoutStep,
      @Named("Transient") Token token,
      EventLoopGroup eventLoopGroup) {
    return new ProbingSequence.Builder()
        .addToken(token)
        .addStep(helloStep)
        .makeFirstRepeated()
        .addStep(loginStep)
        .addStep(logoutStep)
        .eventLoopGroup(eventLoopGroup)
        .build();
  }

  @Provides
  @Named("Epp-Complex")
  ProbingSequence provideComplexEppSequence(
      @Named("Hello") ProbingStep helloStep,
      @Named("Login") ProbingStep loginStep,
      @Named("Create") ProbingStep createStep,
      @Named("Delete") ProbingStep deleteStep,
      @Named("Logout") ProbingStep logoutStep,
      @Named("Transient") Token token,
      EventLoopGroup eventLoopGroup) {
    return new ProbingSequence.Builder()
        .addToken(token)
        .addStep(helloStep)
        .makeFirstRepeated()
        .addStep(loginStep)
        .addStep(createStep)
        .addStep(deleteStep)
        .addStep(logoutStep)
        .eventLoopGroup(eventLoopGroup)
        .build();
  }

  @Provides
  @HttpWhoisProtocol
  int provideHttpWhoisPort() {
    return httpWhoIsPort;
  }

  @Provides
  @HttpsWhoisProtocol
  int provideHttpsWhoisPort() {
    return httpsWhoIsPort;
  }

  @Provides
  @EppProtocol
  int provideEppPort() {
    return eppPort;
  }

  @Provides
  @Singleton
  static Clock provideClock() {
    return new SystemClock();
  }

  @Provides
  @Singleton
  static TimerHandler provideTimerHandler(Clock clock) {
    return new TimerHandler(clock);
  }

  @Provides
  static MetricsHandler provideMetricsHandlers(
      MetricsCollector collector,
      TimerHandler timer) {
    return new MetricsHandler(timer, collector);
  }

  @Provides
  ImmutableMap<Integer, Protocol> providePortToProtocolMap(
      Set<Protocol> protocolSet) {
    return Maps.uniqueIndex(protocolSet, Protocol::port);
  }

  @Singleton
  @Provides
  ProberConfig provideProxyConfig(Environment env) {
    return getProberConfig(env);
  }


  /**
   * Main {@link Dagger} {@link Component} that supplies all necessary components
   * to successfully run a Prober with multiple probingSequences
   */
  @Singleton
  @Component(
      modules = {
          ProberModule.class,
          WebWhoisModule.class,
          EppModule.class,
          TokenModule.class,
          CertificateModule.class
      })
  public interface ProberComponent {

    @HttpWhoisProtocol
    ProbingSequence provideHttpWhoisSequence();

    @HttpsWhoisProtocol
    ProbingSequence provideHttpsWhoisSequence();

    @Named("Epp-Basic")
    ProbingSequence provideBasicEppSequence();

    @Named("Epp-Complex")
    ProbingSequence provideComplexEppSequence();

    ImmutableMap<Integer, Protocol> providePortToProtocolMap();


  }
}
