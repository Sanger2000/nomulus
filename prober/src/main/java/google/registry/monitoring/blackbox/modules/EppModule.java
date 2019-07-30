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
import google.registry.monitoring.blackbox.messages.EppRequestMessage.CheckSuccess;
import google.registry.monitoring.blackbox.messages.EppRequestMessage.Create;
import google.registry.monitoring.blackbox.messages.EppRequestMessage.Delete;
import google.registry.monitoring.blackbox.messages.EppRequestMessage.Hello;
import google.registry.monitoring.blackbox.messages.EppRequestMessage.Login;
import google.registry.monitoring.blackbox.messages.EppRequestMessage.Logout;
import google.registry.monitoring.blackbox.tokens.EppToken;
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

/** A module that provides the {@link Protocol}s to send HTTP(S) web WHOIS requests. */
@Module
public class EppModule {

  private static final int EPP_PORT = 700;

  private static final String EPP_PROTOCOL_NAME = "epp";


  /**
   * Dagger qualifier to provide EPP protocol related handlers and other bindings.
   */
  @Qualifier
  public @interface EppProtocol {}

  @Provides
  @Named("eppLoginLogout")
  static ProbingSequence provideEppLoginLogoutProbingSequence(
      EppToken.Transient token,
      Provider<Bootstrap> bootstrapProvider,
      @Named("Hello") ProbingStep.Builder helloStepBuilder,
      @Named("Login") ProbingStep.Builder loginStepBuilder,
      @Named("Logout") ProbingStep.Builder logoutStepBuilder) {
    return new ProbingSequence.Builder()
        .setBootstrap(bootstrapProvider.get())
        .addToken(token)
        .addStep(helloStepBuilder)
        .addStep(loginStepBuilder)
        .addStep(logoutStepBuilder)
        .build();
  }

  @Provides
  @Named("eppLoginCreateDeleteLogout")
  static ProbingSequence provideEppLoginCreateDeleteLogoutProbingSequence(
      EppToken.Transient token,
      Provider<Bootstrap> bootstrapProvider,
      @Named("Hello") ProbingStep.Builder helloStepBuilder,
      @Named("Login") ProbingStep.Builder loginStepBuilder,
      @Named("Create") ProbingStep.Builder createStepBuilder,
      @Named("Delete") ProbingStep.Builder deleteStepBuilder,
      @Named("Logout") ProbingStep.Builder logoutStepBuilder) {
    return new ProbingSequence.Builder()
        .setBootstrap(bootstrapProvider.get())
        .addToken(token)
        .addStep(helloStepBuilder)
        .addStep(loginStepBuilder)
        .addStep(createStepBuilder)
        .addStep(deleteStepBuilder)
        .addStep(logoutStepBuilder)
        .build();
  }

  @Provides
  @Named("eppLoginCreateCheckDeleteCheckLogout")
  static ProbingSequence provideEppLoginCreateCheckDeleteCheckLogoutProbingSequence(
      EppToken.Transient token,
      Provider<Bootstrap> bootstrapProvider,
      @Named("Hello") ProbingStep.Builder helloStepBuilder,
      @Named("Login") ProbingStep.Builder loginStepBuilder,
      @Named("Create") ProbingStep.Builder createStepBuilder,
      @Named("Check") ProbingStep.Builder checkStepFirstBuilder,
      @Named("Delete") ProbingStep.Builder deleteStepBuilder,
      @Named("Check") ProbingStep.Builder checkStepSecondBuilder,
      @Named("Logout") ProbingStep.Builder logoutStepBuilder) {
    return new ProbingSequence.Builder()
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


  @Provides
  @Named("Hello")
  static ProbingStep.Builder provideEppHelloStepBuilder(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      Hello helloRequest) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(helloRequest);
  }

  @Provides
  @Named("Login")
  static ProbingStep.Builder provideEppLoginStepBuilder(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      Login loginRequest) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(loginRequest);
  }

  @Provides
  @Named("Create")
  static ProbingStep.Builder provideEppCreateStepBuilder(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      Create createRequest) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(createRequest);
  }

    @Provides
    @Named("Check")
    static ProbingStep.Builder provideEppCheckStepBuilder(
        @EppProtocol Protocol eppProtocol,
        Duration duration,
        CheckSuccess checkRequest) {
      return ProbingStep.builder()
          .setProtocol(eppProtocol)
          .setDuration(duration)
          .setMessageTemplate(checkRequest);
  }

  @Provides
  @Named("Delete")
  static ProbingStep.Builder provideEppDeleteStepBuilder(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      Delete deleteRequest) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(deleteRequest);
  }

  @Provides
  @Named("Logout")
  static ProbingStep.Builder provideEppLogoutStepBuilder(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      Logout logoutRequest) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(logoutRequest);
  }



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
