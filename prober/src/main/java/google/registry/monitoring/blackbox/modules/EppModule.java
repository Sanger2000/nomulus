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

package google.registry.monitoring.blackbox.modules;

import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;

import static google.registry.util.ResourceUtils.readResourceUtf8;

import google.registry.monitoring.blackbox.ProbingSequence;
import google.registry.monitoring.blackbox.ProbingStep;
import google.registry.monitoring.blackbox.connection.Protocol;
import google.registry.monitoring.blackbox.handlers.EppActionHandler;
import google.registry.monitoring.blackbox.handlers.EppMessageHandler;
import google.registry.monitoring.blackbox.handlers.SslClientInitializer;
import google.registry.monitoring.blackbox.messages.EppRequestMessage;
import google.registry.monitoring.blackbox.messages.EppRequestMessage.Check;
import google.registry.monitoring.blackbox.messages.EppResponseMessage;
import google.registry.monitoring.blackbox.metrics.MetricsCollector;
import google.registry.monitoring.blackbox.tokens.EppToken;
import google.registry.util.Clock;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslProvider;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.joda.time.Duration;

/** A module that provides the components necessary for and the overall
 * {@link ProbingSequence} to probe EPP. */
@Module
public class EppModule {

  private static final int EPP_PORT = 700;
  private static final String EPP_PROTOCOL_NAME = "epp";


  /** Dagger qualifier to provide EPP protocol related handlers and other bindings. */
  @Qualifier
  public @interface EppProtocol {}

  /** Dagger provided {@link ProbingSequence} that probes EPP login and logout actions. */
  @Provides
  @Named("eppLoginLogout")
  static ProbingSequence provideEppLoginLogoutProbingSequence(
      MetricsCollector metrics,
      Clock clock,
      Provider<Bootstrap> bootstrapProvider,
      EppToken.Transient token,
      @Named("hello") ProbingStep.Builder helloStepBuilder,
      @Named("loginSuccess") ProbingStep.Builder loginStepBuilder,
      @Named("logout") ProbingStep.Builder logoutStepBuilder) {
    return new ProbingSequence.Builder()
        .setMetrics(metrics)
        .setClock(clock)
        .setBootstrap(bootstrapProvider.get())
        .addToken(token)
        .addStep(helloStepBuilder)
        .addStep(loginStepBuilder)
        .addStep(logoutStepBuilder)
        .build();
  }

  /** Dagger provided {@link ProbingSequence} that probes EPP login, create, delete, and logout actions. */
  @Provides
  @Named("eppLoginCreateDeleteLogout")
  static ProbingSequence provideEppLoginCreateDeleteLogoutProbingSequence(
      MetricsCollector metrics,
      Clock clock,
      Provider<Bootstrap> bootstrapProvider,
      EppToken.Transient token,
      @Named("hello") ProbingStep.Builder helloStepBuilder,
      @Named("loginSuccess") ProbingStep.Builder loginStepBuilder,
      @Named("createSuccess") ProbingStep.Builder createStepBuilder,
      @Named("deleteSuccess") ProbingStep.Builder deleteStepBuilder,
      @Named("logout") ProbingStep.Builder logoutStepBuilder) {
    return new ProbingSequence.Builder()
        .setMetrics(metrics)
        .setClock(clock)
        .setBootstrap(bootstrapProvider.get())
        .addToken(token)
        .addStep(helloStepBuilder)
        .addStep(loginStepBuilder)
        .addStep(createStepBuilder)
        .addStep(deleteStepBuilder)
        .addStep(logoutStepBuilder)
        .build();
  }

  /** Dagger provided {@link ProbingSequence} that probes EPP login, create, check, delete, and logout actions. */
  @Provides
  @Named("eppLoginCreateCheckDeleteCheckLogout")
  static ProbingSequence provideEppLoginCreateCheckDeleteCheckLogoutProbingSequence(
      MetricsCollector metrics,
      Clock clock,
      Provider<Bootstrap> bootstrapProvider,
      EppToken.Transient token,
      @Named("hello") ProbingStep.Builder helloStepBuilder,
      @Named("loginSuccess") ProbingStep.Builder loginStepBuilder,
      @Named("createSuccess") ProbingStep.Builder createStepBuilder,
      @Named("checkExists") ProbingStep.Builder checkStepFirstBuilder,
      @Named("deleteSuccess") ProbingStep.Builder deleteStepBuilder,
      @Named("checkNotExists") ProbingStep.Builder checkStepSecondBuilder,
      @Named("logout") ProbingStep.Builder logoutStepBuilder) {
    return new ProbingSequence.Builder()
        .setMetrics(metrics)
        .setClock(clock)
        .setBootstrap(bootstrapProvider.get())
        .addToken(token)
        .addStep(helloStepBuilder)
        .addStep(loginStepBuilder)
        .addStep(createStepBuilder)
        .addStep(checkStepFirstBuilder)
        .addStep(deleteStepBuilder)
        .addStep(checkStepSecondBuilder)
        .addStep(logoutStepBuilder)
        .build();
  }


  /**
   * Provides {@link ProbingStep.Builder} that, when built, establishes initial connection
   * to EPP server.
   *
   * <p>Always necessary as first step for any EPP {@link ProbingSequence} and first repeated
   * step for any {@link ProbingSequence} that doesn't stay logged in (transient).</p>
   */
  @Provides
  @Named("hello")
  static ProbingStep.Builder provideEppHelloStepBuilder(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("hello") EppRequestMessage helloRequest) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(helloRequest);
  }

  /** {@link Provides} {@link ProbingStep.Builder} that, when built, logs into the EPP server. */
  @Provides
  @Named("loginSuccess")
  static ProbingStep.Builder provideEppLoginStepBuilder(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("loginSuccess") EppRequestMessage loginRequest) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(loginRequest);
  }

  /** {@link Provides} {@link ProbingStep.Builder} that, when built, creates new domain on EPP server. */
  @Provides
  @Named("createSuccess")
  static ProbingStep.Builder provideEppCreateStepBuilder(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("createSuccess") EppRequestMessage createRequest) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(createRequest);
  }

  /** {@link Provides} {@link ProbingStep.Builder} that, when built, checks a domain doesn't exist on EPP server. */
  @Provides
  @Named("checkNotExists")
  static ProbingStep.Builder provideEppCheckNotExistsStepBuilder(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("checkNotExists") EppRequestMessage checkNotExistsRequest) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(checkNotExistsRequest);
  }

  /** {@link Provides} {@link ProbingStep.Builder} that, when built, checks a domain exists on EPP server. */
  @Provides
  @Named("checkExists")
  static ProbingStep.Builder provideEppCheckExistsStepBuilder(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("checkExists") EppRequestMessage checkExistsRequest) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(checkExistsRequest);
  }

  /** {@link Provides} {@link ProbingStep.Builder} that, when built, checks deletes a domain from EPP server. */
  @Provides
  @Named("deleteSuccess")
  static ProbingStep.Builder provideEppDeleteStepBuilder(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("deleteSuccess") EppRequestMessage deleteRequest) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(deleteRequest);
  }

  /** {@link Provides} {@link ProbingStep.Builder} that, when built, logs out of EPP server. */
  @Provides
  @Named("logout")
  static ProbingStep.Builder provideEppLogoutStepBuilder(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("logout") EppRequestMessage logoutRequest) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(logoutRequest);
  }

  /**
   * Set of all possible {@link EppRequestMessage}s paired with their expected {@link EppResponseMessage}s.
   */

  /** {@link Provides} {@link EppRequestMessage.Hello} with only expected response of {@link EppResponseMessage.Greeting}. */
  @Provides
  @Named("hello")
  static EppRequestMessage provideHelloRequestMessage(
      EppResponseMessage.Greeting greetingResponse) {
    return new EppRequestMessage.Hello(greetingResponse);
  }

  /** {@link Provides} {@link EppRequestMessage.Login} with expected response of {@link EppResponseMessage.SimpleSuccess}. */
  @Provides
  @Named("loginSuccess")
  static EppRequestMessage provideLoginSuccessRequestMessage(
      EppResponseMessage.SimpleSuccess simpleSuccessResponse,
      @Named("eppUserId") String userId,
      @Named("eppPassword") String userPassword) {
    return new EppRequestMessage.Login(simpleSuccessResponse, userId, userPassword);
  }

  /** {@link Provides} {@link EppRequestMessage.Login} wit expected response of {@link EppResponseMessage.Failure}. */
  @Provides
  @Named("loginFailure")
  static EppRequestMessage provideLoginFailureRequestMessage(
      EppResponseMessage.Failure failureResponse,
      @Named("eppUserId") String userId,
      @Named("eppPassword") String userPassword) {
    return new EppRequestMessage.Login(failureResponse, userId, userPassword);
  }

  /** {@link Provides} {@link EppRequestMessage.Create} with expected response of {@link EppResponseMessage.SimpleSuccess}. */
  @Provides
  @Named("createSuccess")
  static EppRequestMessage provideCreateSuccessRequestMessage(
      EppResponseMessage.SimpleSuccess simpleSuccessResponse) {
    return new EppRequestMessage.Create(simpleSuccessResponse);
  }

  /** {@link Provides} {@link EppRequestMessage.Create} with expected response of {@link EppResponseMessage.Failure}. */
  @Provides
  @Named("createFailure")
  static EppRequestMessage provideCreateFailureRequestMessage(
      EppResponseMessage.Failure failureResponse) {
    return new EppRequestMessage.Create(failureResponse);
  }

  /** {@link Provides} {@link EppRequestMessage.Delete} with expected response of {@link EppResponseMessage.SimpleSuccess}. */
  @Provides
  @Named("deleteSuccess")
  static EppRequestMessage provideDeleteSuccessRequestMessage(
      EppResponseMessage.SimpleSuccess simpleSuccessResponse) {
    return new EppRequestMessage.Delete(simpleSuccessResponse);
  }

  /** {@link Provides} {@link EppRequestMessage.Delete} with expected response of {@link EppResponseMessage.Failure}. */
  @Provides
  @Named("deleteFailure")
  static EppRequestMessage provideDeleteFailureRequestMessage(
      EppResponseMessage.Failure failureResponse) {
    return new EppRequestMessage.Delete(failureResponse);
  }

  /** {@link Provides} {@link EppRequestMessage.Logout} with only expected response of {@link EppResponseMessage.SimpleSuccess}. */
  @Provides
  @Named("logout")
  static EppRequestMessage provideLogoutRequestMessage(
      EppResponseMessage.SimpleSuccess simpleSuccessResponse) {
    return new EppRequestMessage.Logout(simpleSuccessResponse);
  }

  /** {@link Provides} {@link EppRequestMessage.Check} with expected response of {@link EppResponseMessage.DomainExists}. */
  @Provides
  @Named("checkExists")
  static EppRequestMessage provideCheckExistsMessage(
      EppResponseMessage.DomainExists domainExistsResponse) {
    return new EppRequestMessage.Check(domainExistsResponse);
  }

  /** {@link Provides} {@link EppRequestMessage.Check} with expected response of {@link EppResponseMessage.DomainNotExists}. */
  @Provides
  @Named("checkNotExists")
  static EppRequestMessage provideCheckNotExistsMessage(
      EppResponseMessage.DomainNotExists domainNotExistsResponse) {
    return new EppRequestMessage.Check(domainNotExistsResponse);
  }

  /** {@link Provides} {@link Protocol} that represents an EPP connection. */
  @Singleton
  @Provides
  @EppProtocol
  static Protocol provideEppProtocol(
      @EppProtocol int eppPort,
      @EppProtocol ImmutableList<Provider<? extends ChannelHandler>> handlerProviders) {
    return Protocol.builder()
        .setName(EPP_PROTOCOL_NAME)
        .setPort(eppPort)
        .setHandlerProviders(handlerProviders)
        .setPersistentConnection(true)
        .build();
  }

  /** {@link Provides} the list of providers of {@link ChannelHandler}s that are used for the EPP Protocol. */
  @Provides
  @EppProtocol
  static ImmutableList<Provider<? extends ChannelHandler>> provideEppHandlerProviders(
      @EppProtocol Provider<SslClientInitializer<NioSocketChannel>> sslClientInitializerProvider,
      Provider<EppMessageHandler> eppMessageHandlerProvider,
      Provider<EppActionHandler> eppActionHandlerProvider) {
    return ImmutableList.of(
        sslClientInitializerProvider,
        eppMessageHandlerProvider,
        eppActionHandlerProvider);
  }

  /** {@link Provides} the {@link SslClientInitializer} used for the {@link EppProtocol}. */
  @Provides
  @EppProtocol
  static SslClientInitializer<NioSocketChannel> provideSslClientInitializer(
      SslProvider sslProvider,
      Provider<PrivateKey> privateKeyProvider,
      Provider<X509Certificate[]> certificatesProvider) {

    return new SslClientInitializer<>(sslProvider, privateKeyProvider, certificatesProvider);
  }

  @Provides
  @Named("eppUserId")
  static String provideEppUserId() {
    return readResourceUtf8(EppModule.class, "secrets/user_id.txt");
  }

  @Provides
  @Named("eppPassword")
  static String provideEppPassphrase() {
    return readResourceUtf8(EppModule.class, "secrets/password.txt");
  }

  @Provides
  @Named("eppHost")
  static String provideEppHost() {
    return readResourceUtf8(EppModule.class, "secrets/epp_host.txt");
  }

  @Provides
  @Named("eppTld")
  static String provideTld() {
    return "oa-0.test";
  }

  @Provides
  @EppProtocol
  static int provideEppPort() {
    return EPP_PORT;
  }
}
