package google.registry.monitoring.blackbox.messages;

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.util.Map;
import org.w3c.dom.Document;

public abstract class EppRequestMessage extends EppMessage implements OutboundMessageType {

  private final static String DOMAIN_KEY = "//domainns:name";
  private final static String CLIENT_ID_KEY = "//eppns:clID";
  private final static String CLIENT_PASSWORD_KEY = "//eppns:pw";
  private final static String CLIENT_TRID_KEY = "//eppns:clTRID";



  protected Document message;
  protected ImmutableMap.Builder<String, String> replacements;
  private String template;


  public EppRequestMessage modifyMessage(String newDomain) throws IOException, EppClientException {
    Map<String, String> nextArguments = replacements
        .put(DOMAIN_KEY, newDomain)
        .build();
    message = getEppDocFromTemplate(template, nextArguments);
    return this;
  }


  private EppRequestMessage(String template, ImmutableMap.Builder<String, String> replacements) {
    this.template = template;
  }

  public ByteBuf bytes() throws EppClientException{
    byte[] bytestream = xmlDocToByteArray(message);
    int capacity = HEADER_LENGTH + bytestream.length;

    ByteBuf buf = Unpooled.buffer(capacity);

    buf.writeInt(capacity);
    buf.writeBytes(bytestream);

    return buf;
  }

  public static class HELLO extends EppRequestMessage {
    private static final String template = "hello.xml";
    public HELLO(String clTRID) {
      super(
          template,
          ImmutableMap.<String, String>builder()
            .put(CLIENT_TRID_KEY, clTRID)
      );
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) throws IOException, EppClientException{
      message = getEppDocFromTemplate(template, replacements.build());
      return this;
    }
  }

  public static class LOGIN extends EppRequestMessage {
    private static final String template = "login.xml";

    public LOGIN(String eppClientId, String eppClientPassword, String clTRID) {
      super(
          template,
          ImmutableMap.<String, String>builder()
              .put(CLIENT_ID_KEY, eppClientId)
              .put(CLIENT_PASSWORD_KEY, eppClientPassword)
              .put(CLIENT_TRID_KEY, clTRID)
      );
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) throws IOException, EppClientException{
      message = getEppDocFromTemplate(template, replacements.build());
      return this;
    }
  }

  public static class CHECK extends EppRequestMessage {
    private static final String template = "check.xml";

    public CHECK(String clTRID) {
      super(
          template,
          ImmutableMap.<String, String>builder()
          .put(CLIENT_TRID_KEY, clTRID)
      );
    }
  }

  public static class CLAIMSCHECK extends EppRequestMessage {
    private static final String template = "claimscheck.xml";

    public CLAIMSCHECK(String clTRID) {
      super(
          template,
          ImmutableMap.<String, String>builder()
              .put(CLIENT_TRID_KEY, clTRID)
      );
    }
  }

  public static class CREATE extends EppRequestMessage {
    private static final String template = "create.xml";

    public CREATE(String clTRID) {
      super(
          template,
          ImmutableMap.<String, String>builder()
              .put(CLIENT_TRID_KEY, clTRID)
      );
    }
  }

  public static class DELETE extends EppRequestMessage {
    private static final String template = "delete.xml";
    public DELETE(String clTRID) {
      super(
          template,
          ImmutableMap.<String, String>builder()
              .put(CLIENT_TRID_KEY, clTRID)
      );
    }
  }

  public static class LOGOUT extends EppRequestMessage {
    private static final String template = "logout.xml";

    public LOGOUT(String clTRID) {
      super(
          template,
          ImmutableMap.<String, String>builder()
              .put(CLIENT_TRID_KEY, clTRID)
      );
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) throws IOException, EppClientException{
      message = getEppDocFromTemplate(template, replacements.build());
      return this;
    }
  }


}
