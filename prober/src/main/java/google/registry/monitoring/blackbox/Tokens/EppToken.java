package google.registry.monitoring.blackbox.Tokens;

import google.registry.monitoring.blackbox.exceptions.EppClientException;
import google.registry.monitoring.blackbox.exceptions.InternalException;
import google.registry.monitoring.blackbox.messages.EppRequestMessage;
import google.registry.monitoring.blackbox.messages.OutboundMessageType;
import java.io.IOException;
import javax.inject.Inject;

public class EppToken extends Token {


  private static final int MAX_DOMAIN_PART_LENGTH = 50;
  private static int clientIdSuffix = 0;

  private String tld = "app";
  private String host;
  private String currentDomainName;

  @Inject
  public EppToken() {
    currentDomainName = newDomainName(getNewTRID());
  }

  @Override
  public OutboundMessageType modifyMessage(OutboundMessageType originalMessage)
      throws InternalException {
    return ((EppRequestMessage) originalMessage).modifyMessage(
        getNewTRID(),
        currentDomainName
    );
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public Token next() {
    return new EppToken();
  }

  /**
   * Return a unique string usable as an EPP client transaction ID.
   *
   * <p><b>Warning:</b> The prober cleanup servlet relies on the timestamp being in the third
   * position when splitting on dashes. Do not change this format without updating that code as
   * well.
   */
  private synchronized String getNewTRID() {
    return String.format("prober-%s-%d-%d",
        localHostname,
        System.currentTimeMillis(),
        clientIdSuffix++);
  }

  /**
   * Return a fully qualified domain label to use, derived from the client transaction ID.
   */
  private String newDomainName(String clTRID) {
    String sld;
    // not sure if the local hostname will stick to RFC validity rules
    if (clTRID.length() > MAX_DOMAIN_PART_LENGTH) {
      sld = clTRID.substring(clTRID.length() - MAX_DOMAIN_PART_LENGTH);
    } else {
      sld = clTRID;
    }
    //insert top level domain here
    return String.format("%s.%s", sld, tld);
  }

}
