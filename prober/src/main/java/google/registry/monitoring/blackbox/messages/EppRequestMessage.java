package google.registry.monitoring.blackbox.messages;

import static java.nio.charset.StandardCharsets.US_ASCII;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public abstract class EppRequestMessage implements OutboundMarker {
  private static int HEADER_LENGTH = 4;
  private static String PLACE_HOLDER = "PLACE_HOLDER_STRING";
  protected String template;
  protected String message;

  public abstract EppRequestMessage modifyMessage(String newDomain);

  private EppRequestMessage(String template) {
    this.template = template;
  }

  public ByteBuf bytes() {
    byte[] bytestream = message.getBytes(US_ASCII);
    int capacity = HEADER_LENGTH + bytestream.length;

    ByteBuf buf = Unpooled.buffer(capacity);

    buf.writeInt(capacity);
    buf.writeBytes(bytestream);

    return buf;
  }
  public static class HELLO extends EppRequestMessage {
    public HELLO(String content, String host, String path) {
      super(content);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }

  public static class LOGIN extends EppRequestMessage {
    public LOGIN(String content, String host, String path) {
      super(content);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }

  public static class CHECK extends EppRequestMessage {
    public CHECK(String content, String host, String path) {
      super(content);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }

  public static class CLAIMSCHECK extends EppRequestMessage {
    public CLAIMSCHECK(String content, String host, String path) {
      super(content);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }

  public static class CREATE extends EppRequestMessage {
    public CREATE(String content, String host, String path) {
      super(content);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }
  public static class DELETE extends EppRequestMessage {
    public DELETE(String content, String host, String path) {
      super(content);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }
  public static class LOGOUT extends EppRequestMessage {
    public LOGOUT(String content, String host, String path) {
      super(content);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }


}
