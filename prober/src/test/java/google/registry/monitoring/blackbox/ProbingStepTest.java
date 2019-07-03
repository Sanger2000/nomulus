package google.registry.monitoring.blackbox;

import com.google.common.collect.ImmutableList;
import google.registry.monitoring.blackbox.handlers.ActionHandler;
import google.registry.monitoring.blackbox.handlers.TestActionHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import javax.inject.Provider;
import org.junit.Test;

public class ProbingStepTest {

  private ActionHandler<Object, Object> testHandler = new TestActionHandler();
  private Provider<? extends ChannelHandler> provider = new TestProvider<>(testHandler);
  private final LocalAddress address = new LocalAddress("TEST_ADDRESS");

  private ProbingAction<Object> newChannelAction;
  private ProbingAction<Object> existingChannelAction;
  private EmbeddedChannel channel;
  private Protocol protocol = Protocol.builder()
      .handlerProviders(ImmutableList.of(provider))
      .name("TEST_PROTOCOL")
      .port(0)
      .build()
      .address(address);

  ProbingStepWeb<LocalChannel> webStep = new ProbingStepWeb<>(protocol);

  @Test
  public void testGeneralBehavior() {

  }
}
