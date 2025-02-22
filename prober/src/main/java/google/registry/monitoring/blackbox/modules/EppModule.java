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

import static google.registry.monitoring.blackbox.messages.EppRequestMessage.CLIENT_ID_KEY;
import static google.registry.monitoring.blackbox.messages.EppRequestMessage.CLIENT_PASSWORD_KEY;
import static google.registry.monitoring.blackbox.messages.EppRequestMessage.CLIENT_TRID_KEY;
import static google.registry.monitoring.blackbox.messages.EppRequestMessage.DOMAIN_KEY;
import static google.registry.util.ResourceUtils.readResourceUtf8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import google.registry.monitoring.blackbox.ProbingSequence;
import google.registry.monitoring.blackbox.ProbingStep;
import google.registry.monitoring.blackbox.connection.Protocol;
import google.registry.monitoring.blackbox.handlers.EppActionHandler;
import google.registry.monitoring.blackbox.handlers.EppMessageHandler;
import google.registry.monitoring.blackbox.handlers.SslClientInitializer;
import google.registry.monitoring.blackbox.messages.EppMessage;
import google.registry.monitoring.blackbox.messages.EppRequestMessage;
import google.registry.monitoring.blackbox.messages.EppResponseMessage;
import google.registry.monitoring.blackbox.modules.CertificateModule.LocalSecrets;
import google.registry.monitoring.blackbox.tokens.EppToken;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslProvider;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.function.Supplier;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.joda.time.Duration;

/**
 * A module that provides the components necessary for and the overall {@link ProbingSequence} to
 * probe EPP.
 */
@Module
public class EppModule {

  private static final int EPP_PORT = 700;
  private static final String EPP_PROTOCOL_NAME = "epp";

  /** Maximum length of EPP messages received. Same as max length for HTTP messages, is 0.5 MB. */
  // TODO - replace with config specified maximum length.
  private static final int maximumMessageLengthBytes = 512 * 1024;

  /** Standard EPP header length. */
  // TODO - replace with config specified header length (still 4).
  private static final int eppHeaderLengthBytes = 4;

  /** Dagger provided {@link ProbingSequence} that probes EPP login and logout actions. */
  @Provides
  @Singleton
  @EppProtocol
  static Bootstrap provideEppBootstrap(Provider<Bootstrap> bootstrapProvider) {
    return bootstrapProvider.get();
  }

  /**
   * Dagger provided {@link ProbingSequence} that probes EPP login, create, check, and delete
   * actions with a persistent connection.
   */
  @Provides
  @Singleton
  @IntoSet
  static ProbingSequence provideEppLoginCreateCheckDeleteCheckProbingSequence(
      EppToken.Persistent token,
      @Named("hello") ProbingStep helloStep,
      @Named("loginSuccess") ProbingStep loginSuccessStep,
      @Named("createSuccess") ProbingStep createSuccessStep,
      @Named("checkExists") ProbingStep checkStepFirst,
      @Named("deleteSuccess") ProbingStep deleteSuccessStep,
      @Named("checkNotExists") ProbingStep checkStepSecond) {
    return new ProbingSequence.Builder(token)
        .add(helloStep)
        .add(loginSuccessStep)
        .add(createSuccessStep)
        .markFirstRepeated()
        .add(checkStepFirst)
        .add(deleteSuccessStep)
        .add(checkStepSecond)
        .build();
  }

  /**
   * Dagger provided {@link ProbingSequence} that probes EPP login, create, check, delete, and
   * logout actions with a transient connection.
   */
  @Provides
  @Singleton
  @IntoSet
  static ProbingSequence provideEppLoginCreateCheckDeleteCheckLogoutProbingSequence(
      EppToken.Transient token,
      @Named("hello") ProbingStep helloStep,
      @Named("loginSuccess") ProbingStep loginSuccessStep,
      @Named("createSuccess") ProbingStep createSuccessStep,
      @Named("checkExists") ProbingStep checkStepFirst,
      @Named("deleteSuccess") ProbingStep deleteSuccessStep,
      @Named("checkNotExists") ProbingStep checkStepSecond,
      @Named("logout") ProbingStep logoutStep) {
    return new ProbingSequence.Builder(token)
        .add(helloStep)
        .add(loginSuccessStep)
        .add(createSuccessStep)
        .add(checkStepFirst)
        .add(deleteSuccessStep)
        .add(checkStepSecond)
        .add(logoutStep)
        .build();
  }

  /**
   * Provides {@link ProbingStep} that establishes initial connection to EPP server.
   *
   * <p>Always necessary as first step for any EPP {@link ProbingSequence} and first repeated step
   * for any {@link ProbingSequence} that doesn't stay logged in (transient).
   */
  @Provides
  @Named("hello")
  static ProbingStep provideEppHelloStep(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("hello") EppRequestMessage helloRequest,
      @EppProtocol Bootstrap bootstrap) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(helloRequest)
        .setBootstrap(bootstrap)
        .build();
  }

  /** {@link Provides} {@link ProbingStep} that logs into the EPP server. */
  @Provides
  @Named("loginSuccess")
  static ProbingStep provideEppLoginSuccessStep(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("loginSuccess") EppRequestMessage loginSuccessRequest,
      @EppProtocol Bootstrap bootstrap) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(loginSuccessRequest)
        .setBootstrap(bootstrap)
        .build();
  }

  /** {@link Provides} {@link ProbingStep} that creates a new domain on EPP server. */
  @Provides
  @Named("createSuccess")
  static ProbingStep provideEppCreateSuccessStep(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("createSuccess") EppRequestMessage createSuccessRequest,
      @EppProtocol Bootstrap bootstrap) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(createSuccessRequest)
        .setBootstrap(bootstrap)
        .build();
  }

  /** {@link Provides} {@link ProbingStep} that built, checks a domain exists on EPP server. */
  @Provides
  @Named("checkExists")
  static ProbingStep provideEppCheckExistsStep(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("checkExists") EppRequestMessage checkExistsRequest,
      @EppProtocol Bootstrap bootstrap) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(checkExistsRequest)
        .setBootstrap(bootstrap)
        .build();
  }

  /** {@link Provides} {@link ProbingStep} that checks a domain doesn't exist on EPP server. */
  @Provides
  @Named("checkNotExists")
  static ProbingStep provideEppCheckNotExistsStep(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("checkNotExists") EppRequestMessage checkNotExistsRequest,
      @EppProtocol Bootstrap bootstrap) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(checkNotExistsRequest)
        .setBootstrap(bootstrap)
        .build();
  }

  /** {@link Provides} {@link ProbingStep} that deletes a domain from EPP server. */
  @Provides
  @Named("deleteSuccess")
  static ProbingStep provideEppDeleteSuccessStep(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("deleteSuccess") EppRequestMessage deleteSuccessRequest,
      @EppProtocol Bootstrap bootstrap) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(deleteSuccessRequest)
        .setBootstrap(bootstrap)
        .build();
  }

  /** {@link Provides} {@link ProbingStep} that logs out of EPP server. */
  @Provides
  @Named("logout")
  static ProbingStep provideEppLogoutStep(
      @EppProtocol Protocol eppProtocol,
      Duration duration,
      @Named("logout") EppRequestMessage logoutRequest,
      @EppProtocol Bootstrap bootstrap) {
    return ProbingStep.builder()
        .setProtocol(eppProtocol)
        .setDuration(duration)
        .setMessageTemplate(logoutRequest)
        .setBootstrap(bootstrap)
        .build();
  }

  /** {@link Provides} hello {@link EppRequestMessage} with only expected response of greeting. */
  @Provides
  @Named("hello")
  static EppRequestMessage provideHelloRequestMessage(
      @Named("greeting") EppResponseMessage greetingResponse) {

    return new EppRequestMessage(greetingResponse, null, (a, b) -> ImmutableMap.of());
  }

  /**
   * Set of all possible {@link EppRequestMessage}s paired with their expected {@link
   * EppResponseMessage}s.
   */

  /** {@link Provides} login {@link EppRequestMessage} with expected response of success. */
  @Provides
  @Named("loginSuccess")
  static EppRequestMessage provideLoginSuccessRequestMessage(
      @Named("success") EppResponseMessage successResponse,
      @Named("login") String loginTemplate,
      @Named("eppUserId") String userId,
      @Named("eppPassword") String userPassword) {
    return new EppRequestMessage(
        successResponse,
        loginTemplate,
        (clTrid, domain) ->
            ImmutableMap.of(
                CLIENT_TRID_KEY, clTrid,
                CLIENT_ID_KEY, userId,
                CLIENT_PASSWORD_KEY, userPassword));
  }

  /** {@link Provides} login {@link EppRequestMessage} with expected response of failure. */
  @Provides
  @Named("loginFailure")
  static EppRequestMessage provideLoginFailureRequestMessage(
      @Named("failure") EppResponseMessage failureResponse,
      @Named("login") String loginTemplate,
      @Named("eppUserId") String userId,
      @Named("eppPassword") String userPassword) {
    return new EppRequestMessage(
        failureResponse,
        loginTemplate,
        (clTrid, domain) ->
            ImmutableMap.of(
                CLIENT_TRID_KEY, clTrid,
                CLIENT_ID_KEY, userId,
                CLIENT_PASSWORD_KEY, userPassword));
  }

  /** {@link Provides} create {@link EppRequestMessage} with expected response of success. */
  @Provides
  @Named("createSuccess")
  static EppRequestMessage provideCreateSuccessRequestMessage(
      @Named("success") EppResponseMessage successResponse,
      @Named("create") String createTemplate) {
    return new EppRequestMessage(
        successResponse,
        createTemplate,
        (clTrid, domain) ->
            ImmutableMap.of(
                CLIENT_TRID_KEY, clTrid,
                DOMAIN_KEY, domain));
  }

  /** {@link Provides} create {@link EppRequestMessage} with expected response of failure. */
  @Provides
  @Named("createFailure")
  static EppRequestMessage provideCreateFailureRequestMessage(
      @Named("failure") EppResponseMessage failureResponse,
      @Named("create") String createTemplate) {
    return new EppRequestMessage(
        failureResponse,
        createTemplate,
        (clTrid, domain) ->
            ImmutableMap.of(
                CLIENT_TRID_KEY, clTrid,
                DOMAIN_KEY, domain));
  }

  /** {@link Provides} delete {@link EppRequestMessage} with expected response of success. */
  @Provides
  @Named("deleteSuccess")
  static EppRequestMessage provideDeleteSuccessRequestMessage(
      @Named("success") EppResponseMessage successResponse,
      @Named("delete") String deleteTemplate) {
    return new EppRequestMessage(
        successResponse,
        deleteTemplate,
        (clTrid, domain) ->
            ImmutableMap.of(
                CLIENT_TRID_KEY, clTrid,
                DOMAIN_KEY, domain));
  }

  /** {@link Provides} delete {@link EppRequestMessage} with expected response of failure. */
  @Provides
  @Named("deleteFailure")
  static EppRequestMessage provideDeleteFailureRequestMessage(
      @Named("failure") EppResponseMessage failureResponse,
      @Named("delete") String deleteTemplate) {
    return new EppRequestMessage(
        failureResponse,
        deleteTemplate,
        (clTrid, domain) ->
            ImmutableMap.of(
                CLIENT_TRID_KEY, clTrid,
                DOMAIN_KEY, domain));
  }

  /** {@link Provides} logout {@link EppRequestMessage} with expected response of success. */
  @Provides
  @Named("logout")
  static EppRequestMessage provideLogoutSuccessRequestMessage(
      @Named("success") EppResponseMessage successResponse,
      @Named("logout") String logoutTemplate) {
    return new EppRequestMessage(
        successResponse,
        logoutTemplate,
        (clTrid, domain) -> ImmutableMap.of(CLIENT_TRID_KEY, clTrid));
  }

  /** {@link Provides} check {@link EppRequestMessage} with expected response of domainExists. */
  @Provides
  @Named("checkExists")
  static EppRequestMessage provideCheckExistsRequestMessage(
      @Named("domainExists") EppResponseMessage domainExistsResponse,
      @Named("check") String checkTemplate) {
    return new EppRequestMessage(
        domainExistsResponse,
        checkTemplate,
        (clTrid, domain) ->
            ImmutableMap.of(
                CLIENT_TRID_KEY, clTrid,
                DOMAIN_KEY, domain));
  }

  /** {@link Provides} check {@link EppRequestMessage} with expected response of domainExists. */
  @Provides
  @Named("checkNotExists")
  static EppRequestMessage provideCheckNotExistsRequestMessage(
      @Named("domainNotExists") EppResponseMessage domainNotExistsResponse,
      @Named("check") String checkTemplate) {
    return new EppRequestMessage(
        domainNotExistsResponse,
        checkTemplate,
        (clTrid, domain) ->
            ImmutableMap.of(
                CLIENT_TRID_KEY, clTrid,
                DOMAIN_KEY, domain));
  }

  @Provides
  @Named("success")
  static EppResponseMessage provideSuccessResponse() {
    return new EppResponseMessage(
        "success",
        (clTrid, domain) ->
            ImmutableList.of(
                String.format("//eppns:clTRID[.='%s']", clTrid), EppMessage.XPASS_EXPRESSION));
  }

  @Provides
  @Named("failure")
  static EppResponseMessage provideFailureResponse() {
    return new EppResponseMessage(
        "failure",
        (clTrid, domain) ->
            ImmutableList.of(
                String.format("//eppns:clTRID[.='%s']", clTrid), EppMessage.XFAIL_EXPRESSION));
  }

  @Provides
  @Named("domainExists")
  static EppResponseMessage provideDomainExistsResponse() {
    return new EppResponseMessage(
        "domainExists",
        (clTrid, domain) ->
            ImmutableList.of(
                String.format("//eppns:clTRID[.='%s']", clTrid),
                String.format("//domainns:name[@avail='false'][.='%s']", domain),
                EppMessage.XPASS_EXPRESSION));
  }

  @Provides
  @Named("domainNotExists")
  static EppResponseMessage provideDomainNotExistsResponse() {
    return new EppResponseMessage(
        "domainNotExists",
        (clTrid, domain) ->
            ImmutableList.of(
                String.format("//eppns:clTRID[.='%s']", clTrid),
                String.format("//domainns:name[@avail='true'][.='%s']", domain),
                EppMessage.XPASS_EXPRESSION));
  }

  @Provides
  @Named("greeting")
  static EppResponseMessage provideGreetingResponse() {
    return new EppResponseMessage(
        "greeting", (clTrid, domain) -> ImmutableList.of("//eppns:greeting"));
  }

  /** {@link Provides} filename of template for login EPP request. */
  @Provides
  @Named("login")
  static String provideLoginTemplate() {
    return "login.xml";
  }

  /** {@link Provides} filename of template for create EPP request. */
  @Provides
  @Named("create")
  static String provideCreateTemplate() {
    return "create.xml";
  }

  /** {@link Provides} filename of template for delete EPP request. */
  @Provides
  @Named("delete")
  static String provideDeleteTemplate() {
    return "delete.xml";
  }

  /** {@link Provides} filename of template for logout EPP request. */
  @Provides
  @Named("logout")
  static String provideLogoutTemplate() {
    return "logout.xml";
  }

  /** {@link Provides} filename of template for check EPP request. */
  @Provides
  @Named("check")
  static String provideCheckTemplate() {
    return "check.xml";
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

  /**
   * {@link Provides} the list of providers of {@link ChannelHandler}s that are used for the EPP
   * Protocol.
   */
  @Provides
  @EppProtocol
  static ImmutableList<Provider<? extends ChannelHandler>> provideEppHandlerProviders(
      @EppProtocol Provider<SslClientInitializer<NioSocketChannel>> sslClientInitializerProvider,
      Provider<LengthFieldBasedFrameDecoder> lengthFieldBasedFrameDecoderProvider,
      Provider<LengthFieldPrepender> lengthFieldPrependerProvider,
      Provider<EppMessageHandler> eppMessageHandlerProvider,
      Provider<EppActionHandler> eppActionHandlerProvider) {
    return ImmutableList.of(
        sslClientInitializerProvider,
        lengthFieldBasedFrameDecoderProvider,
        lengthFieldPrependerProvider,
        eppMessageHandlerProvider,
        eppActionHandlerProvider);
  }

  @Provides
  static LengthFieldBasedFrameDecoder provideLengthFieldBasedFrameDecoder() {
    return new LengthFieldBasedFrameDecoder(
        maximumMessageLengthBytes,
        0,
        eppHeaderLengthBytes,
        -eppHeaderLengthBytes,
        eppHeaderLengthBytes);
  }

  @Provides
  static LengthFieldPrepender provideLengthFieldPrepender() {
    return new LengthFieldPrepender(eppHeaderLengthBytes, true);
  }

  /** {@link Provides} the {@link SslClientInitializer} used for the {@link EppProtocol}. */
  @Provides
  @EppProtocol
  static SslClientInitializer<NioSocketChannel> provideSslClientInitializer(
      SslProvider sslProvider,
      @LocalSecrets Supplier<PrivateKey> privateKeySupplier,
      @LocalSecrets Supplier<X509Certificate[]> certificatesSupplier) {

    return new SslClientInitializer<>(sslProvider, privateKeySupplier, certificatesSupplier);
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

  /** Dagger qualifier to provide EPP protocol related handlers and other bindings. */
  @Qualifier
  public @interface EppProtocol {}
}
