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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import google.registry.monitoring.blackbox.connection.ProbingAction;
import google.registry.monitoring.blackbox.connection.Protocol;
import google.registry.monitoring.blackbox.exceptions.FailureException;
import google.registry.monitoring.blackbox.exceptions.UndeterminedStateException;
import google.registry.monitoring.blackbox.exceptions.UnrecoverableStateException;
import google.registry.monitoring.blackbox.tokens.Token;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/**
 * Unit Tests on {@link ProbingSequence}
 *
 * <p>First tests the construction of sequences and ensures the ordering is exactly how we expect it
 * to be.
 *
 * <p>Then tests the execution of each step, by ensuring the methods treatment of any kind of
 * response from the {@link ProbingStep}s or {@link ProbingAction}s is what is expected.
 *
 * <p>On every test that runs the sequence, in order for the sequence to stop, we throw an {@link
 * UnrecoverableStateException}, using mocks of the steps or actions, as the sequences are run using
 * the main thread (with {@link EmbeddedChannel}).
 */
@RunWith(JUnit4.class)
public class ProbingSequenceTest {

  /** Default mock {@link ProbingAction} returned when generating an action with a mockStep. */
  private ProbingAction mockAction = Mockito.mock(ProbingAction.class);

  /**
   * Default mock {@link ProbingStep} that will usually return a {@code mockAction} on call to
   * generate action.
   */
  private ProbingStep mockStep = Mockito.mock(ProbingStep.class);

  /** Default mock {@link Token} that is passed into each {@link ProbingSequence} tested. */
  private Token mockToken = Mockito.mock(Token.class);

  /**
   * Default mock {@link Protocol} returned {@code mockStep} and occasionally, other mock {@link
   * ProbingStep}s.
   */
  private Protocol mockProtocol = Mockito.mock(Protocol.class);

  /**
   * {@link EmbeddedChannel} used to create new {@link ChannelPromise} objects returned by mock
   * {@link ProbingAction}s on their {@code call} methods.
   */
  private EmbeddedChannel channel = new EmbeddedChannel();

  @Before
  public void setup() {
    // To avoid a NullPointerException, we must have a protocol return persistent connection as
    // false.
    doReturn(true).when(mockProtocol).persistentConnection();

    // In order to avoid a NullPointerException, we must have the protocol returned that stores
    // persistent connection as false.
    doReturn(mockProtocol).when(mockStep).protocol();

    // Allows for test if channel is accurately set.
    doCallRealMethod().when(mockToken).setChannel(any(Channel.class));
    doCallRealMethod().when(mockToken).channel();

    // Allows call to mockAction to retrieve mocked channel.
    doReturn(channel).when(mockAction).channel();
  }

  @Test
  public void testSequenceBasicConstruction_Success() {
    ProbingStep firstStep = Mockito.mock(ProbingStep.class);
    ProbingStep secondStep = Mockito.mock(ProbingStep.class);
    ProbingStep thirdStep = Mockito.mock(ProbingStep.class);

    ProbingSequence sequence =
        new ProbingSequence.Builder(mockToken)
            .add(firstStep)
            .add(secondStep)
            .add(thirdStep)
            .build();

    assertThat(sequence.get()).isEqualTo(firstStep);
    sequence = sequence.next();

    assertThat(sequence.get()).isEqualTo(secondStep);
    sequence = sequence.next();

    assertThat(sequence.get()).isEqualTo(thirdStep);
    sequence = sequence.next();

    assertThat(sequence.get()).isEqualTo(firstStep);
  }

  @Test
  public void testSequenceAdvancedConstruction_Success() {
    ProbingStep firstStep = Mockito.mock(ProbingStep.class);
    ProbingStep secondStep = Mockito.mock(ProbingStep.class);
    ProbingStep thirdStep = Mockito.mock(ProbingStep.class);

    ProbingSequence sequence =
        new ProbingSequence.Builder(mockToken)
            .add(thirdStep)
            .add(secondStep)
            .markFirstRepeated()
            .add(firstStep)
            .build();

    assertThat(sequence.get()).isEqualTo(thirdStep);
    sequence = sequence.next();

    assertThat(sequence.get()).isEqualTo(secondStep);
    sequence = sequence.next();

    assertThat(sequence.get()).isEqualTo(firstStep);
    sequence = sequence.next();

    assertThat(sequence.get()).isEqualTo(secondStep);
  }

  @Test
  public void testRunStep_Success() throws UndeterminedStateException {
    // Always returns a succeeded future on call to mockAction.
    doReturn(channel.newSucceededFuture()).when(mockAction).call();

    // Has mockStep always return mockAction on call to generateAction.
    doReturn(mockAction).when(mockStep).generateAction(any(Token.class));

    // Dummy step that server purpose of placeholder to test ability of ProbingSequence to move on.
    ProbingStep secondStep = Mockito.mock(ProbingStep.class);
    ProbingAction secondAction = Mockito.mock(ProbingAction.class);

    doReturn(channel.newFailedFuture(new UnrecoverableStateException("")))
        .when(secondAction)
        .call();
    doReturn(secondAction).when(secondStep).generateAction(mockToken);

    // Build testable sequence from mocked components.
    ProbingSequence sequence =
        new ProbingSequence.Builder(mockToken).add(mockStep).add(secondStep).build();

    sequence.start();

    // We expect to have only generated actions from mockStep once, and we expect to have called
    // this generated action only once, as when we move on to secondStep, it terminates the
    // sequence.
    verify(mockStep).generateAction(mockToken);
    verify(mockAction).call();

    // Similarly, we expect to generate actions and call the action from the secondStep once, as
    // after calling it, the sequence should be terminated
    verify(secondStep).generateAction(mockToken);
    verify(secondAction).call();

    // We should have modified the token's channel after the first, succeeded step.
    assertThat(mockToken.channel()).isEqualTo(channel);
  }

  @Test
  public void testRunLoop_Success() throws UndeterminedStateException {
    // Always returns a succeeded future on call to mockAction.
    doReturn(channel.newSucceededFuture()).when(mockAction).call();

    // Has mockStep always return mockAction on call to generateAction
    doReturn(mockAction).when(mockStep).generateAction(mockToken);

    // Dummy step that server purpose of placeholder to test ability of ProbingSequence to move on.
    ProbingStep secondStep = Mockito.mock(ProbingStep.class);
    ProbingAction secondAction = Mockito.mock(ProbingAction.class);

    // Necessary for success of ProbingSequence runStep method as it calls get().protocol().
    doReturn(mockProtocol).when(secondStep).protocol();

    // We ensure that secondStep has necessary attributes to be successful step to pass on to
    // mockStep once more.
    doReturn(channel.newSucceededFuture()).when(secondAction).call();
    doReturn(secondAction).when(secondStep).generateAction(mockToken);

    // We get a secondToken that is returned when we are on our second loop in the sequence. This
    // will inform mockStep on when to generate a different ProbingAction.
    Token secondToken = Mockito.mock(Token.class);
    doReturn(secondToken).when(mockToken).next();

    // The thirdAction we use is made so that when it is called, it will halt the ProbingSequence
    // by returning an UnrecoverableStateException.
    ProbingAction thirdAction = Mockito.mock(ProbingAction.class);
    doReturn(channel.newFailedFuture(new UnrecoverableStateException(""))).when(thirdAction).call();
    doReturn(thirdAction).when(mockStep).generateAction(secondToken);

    // Build testable sequence from mocked components.
    ProbingSequence sequence =
        new ProbingSequence.Builder(mockToken).add(mockStep).add(secondStep).build();

    sequence.start();

    // We expect to have generated actions from mockStep twice (once for mockToken and once for
    // secondToken), and we expectto have called each generated action only once, as when we move
    // on to mockStep the second time, it will terminate the sequence after calling thirdAction.
    verify(mockStep).generateAction(mockToken);
    verify(mockStep).generateAction(secondToken);
    verify(mockAction).call();
    verify(thirdAction).call();

    // Similarly, we expect to generate actions and call the action from the secondStep once, as
    // after calling it, we move on to mockStep again, which terminates the sequence.
    verify(secondStep).generateAction(mockToken);
    verify(secondAction).call();

    // We should have modified the token's channel after the first, succeeded step.
    assertThat(mockToken.channel()).isEqualTo(channel);
  }

  /**
   * Test for when we expect Failure within try catch block of generating and calling a {@link
   * ProbingAction}.
   *
   * @throws UndeterminedStateException - necessary for having mock return anything on a call to
   *     {@code generateAction}.
   */
  private void testActionFailure() throws UndeterminedStateException {
    // Dummy step that server purpose of placeholder to test ability of ProbingSequence to move on.
    ProbingStep secondStep = Mockito.mock(ProbingStep.class);

    // We create a second token that when used to generate an action throws an
    // UnrecoverableStateException to terminate the sequence
    Token secondToken = Mockito.mock(Token.class);
    doReturn(secondToken).when(mockToken).next();
    doThrow(new UnrecoverableStateException("")).when(mockStep).generateAction(secondToken);

    // Build testable sequence from mocked components.
    ProbingSequence sequence =
        new ProbingSequence.Builder(mockToken).add(mockStep).add(secondStep).build();

    sequence.start();

    // We expect that we have generated actions twice. First, when we actually test generateAction
    // with an actual call using mockToken, and second when we throw an
    // UnrecoverableStateException with secondToken.
    verify(mockStep).generateAction(mockToken);
    verify(mockStep).generateAction(secondToken);

    // We should never reach the step where we modify the channel, as it should have failed by then
    assertThat(mockToken.channel()).isNull();
    assertThat(secondToken.channel()).isNull();

    // We should never reach the second step, since we fail on the first step, then terminate on
    // the first step after retrying.
    verify(secondStep, times(0)).generateAction(any(Token.class));
  }

  @Test
  public void testRunStep_FailureRunning() throws UndeterminedStateException {
    // Returns a failed future when calling the generated mock action.
    doReturn(channel.newFailedFuture(new FailureException(""))).when(mockAction).call();

    // Returns mock action on call to generate action for ProbingStep.
    doReturn(mockAction).when(mockStep).generateAction(mockToken);

    // Tests generic behavior we expect when we fail in generating or calling an action.
    testActionFailure();

    // We only expect to have called this action once, as we only get it from one generateAction
    // call.
    verify(mockAction).call();
  }

  @Test
  public void testRunStep_FailureGenerating() throws UndeterminedStateException {
    // Create a mock first step that returns the dummy action when called to generate an action.
    doThrow(UndeterminedStateException.class).when(mockStep).generateAction(mockToken);

    testActionFailure();

    // We expect to have never called this action, as we fail each time whenever generating actions.
    verify(mockAction, times(0)).call();
  }
}
