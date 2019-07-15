package google.registry.monitoring.blackbox.Tokens;

import google.registry.monitoring.blackbox.messages.EppClientException;
import google.registry.monitoring.blackbox.messages.EppRequestMessage;
import google.registry.monitoring.blackbox.messages.OutboundMessageType;
import java.io.IOException;
import javax.inject.Inject;

public class EppToken extends Token {

  private String host;
  private String currentDomainName;

  @Inject
  public EppToken() {}

  @Override
  public OutboundMessageType modifyMessage(OutboundMessageType originalMessage)
      throws IOException, EppClientException {
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
