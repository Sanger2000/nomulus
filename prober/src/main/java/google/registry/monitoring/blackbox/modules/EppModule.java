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
import com.google.common.flogger.FluentLogger;
import dagger.Module;
import dagger.Provides;

import static google.registry.util.ResourceUtils.readResourceUtf8;

import google.registry.monitoring.blackbox.ProbingStep;
import google.registry.monitoring.blackbox.ProbingStepEpp;
import google.registry.monitoring.blackbox.connection.Protocol;
import google.registry.monitoring.blackbox.handlers.EppActionHandler;
import google.registry.monitoring.blackbox.handlers.EppMessageHandler;
import google.registry.monitoring.blackbox.handlers.MessageHandler;
import google.registry.monitoring.blackbox.handlers.MetricsHandler;
import google.registry.monitoring.blackbox.handlers.SslClientInitializer;
import google.registry.monitoring.blackbox.handlers.TimerHandler;
import google.registry.monitoring.blackbox.messages.EppRequestMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslProvider;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Singleton;

/** A module that provides the {@link Protocol}s to send HTTP(S) web WHOIS requests. */
@Module
public class EppModule {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String EPP_PROTOCOL_NAME = "epp";


  /**
   * Dagger qualifier to provide EPP protocol related handlers and other bindings.
   */
  @Qualifier
  public @interface EppProtocol {}

  @Provides
  @Named("Hello")
  static ProbingStep provideEppHelloStep(
      @EppProtocol Protocol eppProtocol,
      EppRequestMessage.HELLO helloRequest) {
    return new ProbingStepEpp(eppProtocol, helloRequest);
  }

  @Provides
  @Named("Login")
  static ProbingStep provideEppLoginStep(
      @EppProtocol Protocol eppProtocol,
      EppRequestMessage.LOGIN loginRequest) {
    return new ProbingStepEpp(eppProtocol, loginRequest);
  }

  @Provides
  @Named("Create")
  static ProbingStep provideEppCreateStep(
      @EppProtocol Protocol eppProtocol,
      EppRequestMessage.CREATE createRequest) {
    return new ProbingStepEpp(eppProtocol, createRequest);
  }

  @Provides
  @Named("Delete")
  static ProbingStep provideEppDeleteStep(
      @EppProtocol Protocol eppProtocol,
      EppRequestMessage.DELETE deleteRequest) {
    return new ProbingStepEpp(eppProtocol, deleteRequest);
  }

  @Provides
  @Named("Logout")
  static ProbingStep provideEppLogoutStep(
      @EppProtocol Protocol eppProtocol,
      EppRequestMessage.LOGOUT logoutRequest) {
    return new ProbingStepEpp(eppProtocol, logoutRequest);
  }



  @Singleton
  @Provides
  @EppProtocol
  static Protocol provideEppProtocol(
      @EppProtocol int eppPort,
      @EppProtocol ImmutableList<Provider<? extends ChannelHandler>> handlerProviders) {
    return Protocol.builder()
        .name(EPP_PROTOCOL_NAME)
        .port(eppPort)
        .handlerProviders(handlerProviders)
        .persistentConnection(true)
        .build();
  }

  @Provides
  @EppProtocol
  static ImmutableList<Provider<? extends ChannelHandler>> provideEppHandlerProviders(
      Provider<TimerHandler> timerHandlerProvider,
      @EppProtocol Provider<SslClientInitializer<NioSocketChannel>> sslClientInitializerProvider,
      @EppProtocol Provider<MessageHandler> messageHandlerProvider,
      Provider<EppActionHandler> eppActionHandlerProvider,
      Provider<MetricsHandler> metricsHandlerProvider) {
    return ImmutableList.of(
        timerHandlerProvider,
        sslClientInitializerProvider,
        messageHandlerProvider,
        eppActionHandlerProvider,
        metricsHandlerProvider);
  }



  @Provides
  @EppProtocol
  static MessageHandler provideMessageHandler() {
    return new EppMessageHandler();
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
  @Named("epp_user_id")
  static String provideEppUserId() {
    return readResourceUtf8(EppModule.class, "secrets/user_id.txt");
  }

  @Provides
  @Named("epp_password")
  static String provideEppPassphrase() {
    return readResourceUtf8(EppModule.class, "secrets/password.txt");
  }

  @Provides
  @Named("Epp-Host")
  static String provideEppHost() {
    return readResourceUtf8(EppModule.class, "secrets/epp_host.txt");
  }

  @Provides
  @Named("Epp-Tld")
  static String provideTld() {
    return "oa-0.test";
  }
}
