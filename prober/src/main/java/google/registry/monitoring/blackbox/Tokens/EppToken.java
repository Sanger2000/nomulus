package google.registry.monitoring.blackbox.Tokens;

import google.registry.monitoring.blackbox.messages.EppRequestMessage;
import google.registry.monitoring.blackbox.messages.OutboundMarker;
import io.netty.channel.Channel;
import javax.inject.Inject;

public class EppToken extends Token {

  private Channel channel;
  private String host;
  private String currentDomainName;

  @Inject
  public EppToken() {}

  @Override
  public Channel channel() {
    return channel;
  }

  @Override
  public OutboundMarker modifyMessage(OutboundMarker originalMessage) {
    return ((EppRequestMessage) originalMessage).modifyMessage(currentDomainName);
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public Token next() {
    return new EppToken();
  }

}
