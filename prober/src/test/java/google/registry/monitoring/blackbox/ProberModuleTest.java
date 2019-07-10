// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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

import static com.google.common.collect.ImmutableList.toImmutableList;


import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import google.registry.monitoring.blackbox.WebWhoisModule.HttpWhoisProtocol;
import google.registry.monitoring.blackbox.WebWhoisModule.HttpsWhoisProtocol;
import google.registry.monitoring.blackbox.handlers.ResponseDowncastHandler;
import google.registry.monitoring.blackbox.handlers.SslClientInitializer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.junit.Before;

/**
 * Base class for end-to-end tests of a {@link Protocol}.
 *
 * <p>The end-to-end tests ensures that the business logic that a {@link Protocol} defines are
 * correctly performed by various handlers attached to its pipeline. Non-business essential handlers
 * should be excluded.
 *
 * <p>Subclass should implement an no-arg constructor that calls constructors of this class,
 * providing the method reference of the {@link TestComponent} method to call to obtain the list of
 * {@link ChannelHandler} providers for the {@link Protocol} to test, and optionally a set of {@link
 * ChannelHandler} classes to exclude from testing.
 */
public abstract class ProberModuleTest {

  //protected static final ProxyConfig PROXY_CONFIG = getProxyConfig(LOCAL);

  protected TestComponent testComponent;

  /**
   * Default list of handler classes that are not of interest in end-to-end testing of the {@link
   * Protocol}.
   */
  private static final ImmutableSet<Class<? extends ChannelHandler>> DEFAULT_EXCLUDED_HANDLERS =
      ImmutableSet.of(
          // The PROXY protocol is only used when the proxy is behind the GCP load balancer. It is
          // not part of any business logic.
          ResponseDowncastHandler.class,
          // SSL is part of the business logic for some protocol (EPP for example), but its
          // impact is isolated. Including it makes tests much more complicated. It should be tested
          // separately in its own unit tests.
          SslClientInitializer.class
      );

  protected EmbeddedChannel channel;

  /**
   * Method reference to the component method that exposes the list of handler providers for the
   * specific {@link Protocol} in interest.
   */
  protected final Function<TestComponent, ImmutableList<Provider<? extends ChannelHandler>>>
      handlerProvidersMethod;

  protected final ImmutableSet<Class<? extends ChannelHandler>> excludedHandlers;

  protected ProberModuleTest(
      Function<TestComponent, ImmutableList<Provider<? extends ChannelHandler>>>
          handlerProvidersMethod,
      ImmutableSet<Class<? extends ChannelHandler>> excludedHandlers) {
    this.handlerProvidersMethod = handlerProvidersMethod;
    this.excludedHandlers = excludedHandlers;
  }

  protected ProberModuleTest(
      Function<TestComponent, ImmutableList<Provider<? extends ChannelHandler>>>
          handlerProvidersMethod) {
    this(handlerProvidersMethod, DEFAULT_EXCLUDED_HANDLERS);
  }

  /** Excludes handler providers that are not of interested for testing. */
  private ImmutableList<Provider<? extends ChannelHandler>> excludeHandlerProvidersForTesting(
      ImmutableList<Provider<? extends ChannelHandler>> handlerProviders) {
    return handlerProviders
        .stream()
        .filter(handlerProvider -> !excludedHandlers.contains(handlerProvider.get().getClass()))
        .collect(toImmutableList());
  }

  protected void initializeChannel(Consumer<Channel> initializer) {
    channel =
        new EmbeddedChannel(
            new ChannelInitializer<Channel>() {
              @Override
              protected void initChannel(Channel ch) throws Exception {
                initializer.accept(ch);
              }
            });
  }

  /** Adds handlers to the channel pipeline, excluding any one in {@link #excludedHandlers}. */
  void addAllTestableHandlers(Channel ch) {
    for (Provider<? extends ChannelHandler> handlerProvider :
        excludeHandlerProvidersForTesting(handlerProvidersMethod.apply(testComponent))) {
      ch.pipeline().addLast(handlerProvider.get());
    }
  }

  static TestComponent makeTestComponent() {
    return DaggerProberModuleTest_TestComponent.builder().build();
  }

  @Before
  public void setUp() throws Exception {
    testComponent = makeTestComponent();
    initializeChannel(this::addAllTestableHandlers);
  }

  /**
   * Component used to obtain the list of {@link ChannelHandler} providers for each {@link
   * Protocol}.
   */
  @Singleton
  @Component(
      modules = {
          TestModule.class,
          WebWhoisModule.class,
          TokenModule.class,
      })
  interface TestComponent {
    @HttpWhoisProtocol
    ImmutableList<Provider<? extends ChannelHandler>> httpWhoisHandlers();

  }

  /**
   * Module that provides bindings used in tests.
   *
   * <p>Most of the binding provided in this module should be either a fake, or a {@link
   * ChannelHandler} that is excluded, and annotated with {@code @Singleton}. This module acts as a
   * replacement for {@link ProxyModule} used in production component. Providing a handler that is
   * part of the business logic of a {@link Protocol} from this module is a sign that the binding
   * should be provided in the respective {@code ProtocolModule} instead.
   */
  @Module
  static class TestModule {


    @Singleton
    @Provides
    static SslProvider provideSslProvider() {
      return SslProvider.JDK;
    }

    @Singleton
    @Provides
    @Named("accessToken")
    static Supplier<String> provideFakeAccessToken() {
      return Suppliers.ofInstance("fake.test.token");
    }

    @Singleton
    @Provides
    static LoggingHandler provideLoggingHandler() {
      return new LoggingHandler();
    }

    @Singleton
    @Provides
    static ExecutorService provideExecutorService() {
      return MoreExecutors.newDirectExecutorService();
    }

    @Singleton
    @Provides
    static ScheduledExecutorService provideScheduledExecutorService() {
      return Executors.newSingleThreadScheduledExecutor();
    }

    // This method is only here to satisfy Dagger binding, but is never used. In test environment,
    // it is the self-signed certificate and its key that end up being used.
    @Singleton
    @Provides
    @Named("pemBytes")
    static byte[] providePemBytes() {
      return new byte[0];
    }
  }
}
