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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.JUnitBackports.assertThrows;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import google.registry.monitoring.blackbox.TestUtils.DummyStep;
import google.registry.monitoring.blackbox.TestUtils.ExistingChannelToken;
import google.registry.monitoring.blackbox.TestUtils.NewChannelToken;
import google.registry.monitoring.blackbox.TestUtils.TestProvider;
import google.registry.monitoring.blackbox.TestUtils.TestStep;
import google.registry.monitoring.blackbox.Tokens.Token;
import google.registry.monitoring.blackbox.handlers.ActionHandler;
import google.registry.monitoring.blackbox.handlers.ConversionHandler;
import google.registry.monitoring.blackbox.handlers.NettyRule;
import google.registry.monitoring.blackbox.handlers.TestActionHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultPromise;
import javax.inject.Provider;
import org.junit.Rule;
import org.junit.Test;

/** Unit Tests for {@link ProbingSequence}s and {@link ProbingStep}s and their specific implementations*/
public class ProbingSequenceStepTest {

  /** Basic Constants necessary for tests */
  private final String ADDRESS_NAME = "TEST_ADDRESS";
  private final String PROTOCOL_NAME = "TEST_PROTOCOL";
  private final int PROTOCOL_PORT = 0;
  private final String TEST_MESSAGE = "TEST_MESSAGE";
  private final String SECONDARY_TEST_MESSAGE = "SECONDARY_TEST_MESSAGE";

  private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
  private final LocalAddress address = new LocalAddress(ADDRESS_NAME);

  /** Used for testing how well probing step can create connection to blackbox server */
  @Rule
  public NettyRule nettyRule = new NettyRule(eventLoopGroup);


  /** The two main handlers we need in any test pipeline used that connects to {@link NettyRule's server}**/
  private ActionHandler testHandler = new TestActionHandler();
  private ChannelHandler conversionHandler = new ConversionHandler();

  /** Wrapper provider classes of these handlers */
  private Provider<? extends ChannelHandler> testHandlerProvider = new TestProvider<>(testHandler);
  private Provider<? extends ChannelHandler> conversionHandlerProvider = new TestProvider<>(conversionHandler);

  /** Embedded Channel and Protocol both are stated, but not specified until we know which test we are running) */
  private EmbeddedChannel channel;
  private Protocol testProtocol;



  /** Fields that correspond to instances of each of the above {@link ProbingStep} classes in the same order */
  private ProbingStep<LocalChannel> firstStep;
  private ProbingStep<LocalChannel> dummyStep;

  /** Never explicitly used, but our ProbingStep depends on the ProbingSequence to function, so we create a declare a throwaway ProbingSequence */
  private ProbingSequence<LocalChannel> testSequence;

  /** We declare the token we feed into our probing step, but will specify what kind it is, depending on if we are creating a new channel or reusing one */
  private Token testToken;

  /** Sets up testToken to return arbitrary values, and no channel. Used when we create a new channel */
  private void setupNewChannelToken() {
    testToken = new NewChannelToken("");
  }

  /** Sets up testToken to return arbitrary value, and the embedded channel. Used for when the ProbingStep generates an ExistingChannelAction */
  private void setupExistingChannelToken() {
    testToken = new ExistingChannelToken(channel, "");
  }

  /** Sets up an embedded channel to contain the two handlers we created already */
  private void setupChannel() {
    channel = new EmbeddedChannel(conversionHandler, testHandler);
  }

  /** Sets up our main step (firstStep) and throwaway step (dummyStep) */
  private void setupSteps() {
    firstStep = new TestStep(testProtocol, TEST_MESSAGE, address);
    dummyStep = new DummyStep(testProtocol, eventLoopGroup);
  }

  /** Sets up testProtocol for when we create a new channel */
  private void setupNewProtocol() {
    testProtocol = Protocol.builder()
        .handlerProviders(ImmutableList.of(conversionHandlerProvider, testHandlerProvider))
        .name(PROTOCOL_NAME)
        .port(PROTOCOL_PORT)
        .persistentConnection(false)
        .build();
  }

  /** Sets up testProtocol for when a channel already exists */
  private void setupExistingProtocol() {
    testProtocol = Protocol.builder()
        .handlerProviders(ImmutableList.of(conversionHandlerProvider, testHandlerProvider))
        .name(PROTOCOL_NAME)
        .port(PROTOCOL_PORT)
        .persistentConnection(true)
        .build();
  }

  /** Builds a sequence with our probing steps and the EventLoopGroup we initialized */
  private void setupSequence() {
    testSequence = new ProbingSequence.Builder<LocalChannel>()
        .eventLoopGroup(eventLoopGroup)
        .setClass(LocalChannel.class)
        .addStep(firstStep)
        .makeFirstRepeated()
        .addStep(dummyStep)
        .build();
  }


  @Test
  public void testGeneralBehavior() {
    //setup
    setupNewProtocol();
    setupSteps();
    setupNewChannelToken();

    //there should be no next step
    assertThat(firstStep.nextStep()).isNull();

    //we expect that this exception be thrown
    assertThrows(NullPointerException.class, () -> firstStep.accept(testToken));

  }

  @Test
  public void testWithSequence_NewChannel() throws Exception {
    //setup
    setupNewProtocol();
    setupSteps();
    setupSequence();
    setupNewChannelToken();

    //checks that the ProbingSteps are appropriately pointing to each other
    assertThat(firstStep.nextStep()).isEqualTo(dummyStep);
    assertThat(dummyStep.nextStep()).isEqualTo(firstStep);

    //Set up blackbox server that recieves our messages then echoes them back to us
    nettyRule.setUpServer(address, new ChannelInboundHandlerAdapter());

    //Call accept on the first step, which should send our message to the server, which will then be
    //echoed back to us, causing us to move to the next step
    firstStep.accept(testToken);

    //Obtains future for when we have moved to the next step
    DefaultPromise<Token> future = ((DummyStep)dummyStep).getFuture();

    //checks that we have appropriately sent the write message to server
    nettyRule.assertThatCustomWorks(TEST_MESSAGE);

    //checks that when the future is successful, we pass down the requisite token
    assertThat(future.get()).isEqualTo(testToken);

  }

  @Test
  public void testWithSequence_ExistingChannel() throws Exception {
    //setup
    setupExistingProtocol();
    setupSteps();
    setupSequence();
    setupChannel();
    setupExistingChannelToken();

    //checks that the ProbingSteps are appropriately pointing to each other
    assertThat(firstStep.nextStep()).isEqualTo(dummyStep);
    assertThat(dummyStep.nextStep()).isEqualTo(firstStep);

    //Call accept on the first step, which should send our message through the EmbeddedChannel pipeline
    firstStep.accept(testToken);

    //Ensures the accurate message is sent down the pipeline
    assertThat(((ByteBuf)channel.readOutbound()).toString(UTF_8)).isEqualTo(TEST_MESSAGE);

    //Obtains future for when we have moved to the next step
    DefaultPromise<Token> future = ((DummyStep)dummyStep).getFuture();

    //Write response to our message down EmbeddedChannel pipeline
    channel.writeInbound(Unpooled.wrappedBuffer(SECONDARY_TEST_MESSAGE.getBytes(US_ASCII)));

    //At this point, we should have received the message, so the future obtained should be marked as a success
    assertThat(future.isSuccess());

    //checks that the requisite token is passed down
    assertThat(future.get()).isEqualTo(testToken);

  }
}
