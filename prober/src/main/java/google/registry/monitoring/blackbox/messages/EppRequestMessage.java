package google.registry.monitoring.blackbox.messages;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

public abstract class EppRequestMessage extends HttpRequestMessage {

  public abstract EppRequestMessage modifyMessage(String newDomain);

  private EppRequestMessage(String content, String host, String path) {
    super(HttpVersion.HTTP_1_1, HttpMethod.POST, path, Unpooled.wrappedBuffer(content.getBytes(US_ASCII)));
    headers().setInt("content-length", content().readableBytes());
  }

  public static class HELLO extends EppRequestMessage {
    public HELLO(String content, String host, String path) {
      super(content, host, path);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }

  public static class LOGIN extends EppRequestMessage {
    public LOGIN(String content, String host, String path) {
      super(content, host, path);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }

  public static class CHECK extends EppRequestMessage {
    public CHECK(String content, String host, String path) {
      super(content, host, path);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }

  public static class CLAIMSCHECK extends EppRequestMessage {
    public CLAIMSCHECK(String content, String host, String path) {
      super(content, host, path);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }

  public static class CREATE extends EppRequestMessage {
    public CREATE(String content, String host, String path) {
      super(content, host, path);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }
  public static class DELETE extends EppRequestMessage {
    public DELETE(String content, String host, String path) {
      super(content, host, path);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }
  public static class LOGOUT extends EppRequestMessage {
    public LOGOUT(String content, String host, String path) {
      super(content, host, path);
    }

    @Override
    public EppRequestMessage modifyMessage(String newDomain) {
      return this;
    }
  }


}
