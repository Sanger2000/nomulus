package google.registry.monitoring.blackbox.messages;

import static java.nio.charset.StandardCharsets.US_ASCII;

import io.netty.buffer.ByteBuf;

public class EppResponseMessage {

  public String decode(ByteBuf buf) {
    int capacity = buf.readInt() - 4;

    return buf.toString(US_ASCII);
  }
}
