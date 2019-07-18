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


import google.registry.monitoring.blackbox.ProbingStep;
import google.registry.monitoring.blackbox.ProbingStepWeb;
import google.registry.monitoring.blackbox.connection.Protocol;
import google.registry.monitoring.blackbox.handlers.EppActionHandler;
import google.registry.monitoring.blackbox.handlers.EppMessageHandler;
import google.registry.monitoring.blackbox.handlers.MessageHandler;
import google.registry.monitoring.blackbox.handlers.SslClientInitializer;
import google.registry.monitoring.blackbox.messages.EppResponseMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslProvider;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.function.Supplier;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Singleton;

/** A module that provides the {@link Protocol}s to send HTTP(S) web WHOIS requests. */
@Module
public class EppModule {

  final static String DOMAIN_SUFFIX = "whois.nic.";
  private static final String EPP_PROTOCOL_NAME = "epp";


  /**
   * Dagger qualifier to provide EPP protocol related handlers and other bindings.
   */
  @Qualifier
  public @interface EppProtocol {}

  @Qualifier
  @interface Login {}

  @Qualifier
  @interface Logout {}


  private static final String HTTP_PROTOCOL_NAME = "whois_http";
  private static final String HTTPS_PROTOCOL_NAME = "whois_https";


  @Provides
  @EppProtocol
  static ProbingStep<NioSocketChannel> provideEppProbingStep(
      @EppProtocol Protocol eppProtocol) {
    return new ProbingStepWeb<>(eppProtocol);
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
  String provideEppHost() {
    return DOMAIN_SUFFIX;
  }


  @Provides
  @EppProtocol
  static ImmutableList<Provider<? extends ChannelHandler>> provideEppHandlerProviders(
      @EppProtocol Provider<SslClientInitializer<NioSocketChannel>> sslClientInitializerProvider,
      @EppProtocol Provider<MessageHandler> messageHandlerProvider,
      Provider<EppActionHandler> eppActionHandlerProvider) {
    return ImmutableList.of(
        sslClientInitializerProvider,
        messageHandlerProvider,
        eppActionHandlerProvider);
  }


  @Provides
  @EppProtocol
  static MessageHandler provideMessageHandler() {
    return new EppMessageHandler(new EppResponseMessage.Success());
  }

  @Provides
  @EppProtocol
  static SslClientInitializer<NioSocketChannel> provideSslClientInitializer(
      SslProvider sslProvider,
      Supplier<PrivateKey> privateKeySupplier,
      Supplier<X509Certificate[]> certificatesSupplier) {

    return new SslClientInitializer<>(sslProvider, privateKeySupplier, certificatesSupplier);
  }
}
