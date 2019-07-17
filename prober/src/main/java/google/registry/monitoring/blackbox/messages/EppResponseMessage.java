package google.registry.monitoring.blackbox.messages;

import com.google.common.collect.ImmutableList;
import google.registry.monitoring.blackbox.exceptions.EppClientException;
import google.registry.monitoring.blackbox.exceptions.ResponseException;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;

public abstract class EppResponseMessage extends EppMessage implements InboundMessageType{
  protected String clTRID;

  public enum ResponseType {SUCCESS, FAILURE, ERROR}

  public abstract void getDocument(String clTRID, ByteBuf buf) throws ResponseException;

  protected void getDocument(ByteBuf buf) throws ResponseException {

    int capacity = buf.readInt() - 4;
    byte[] response = new byte[capacity];

    buf.readBytes(response);
    message = byteArrayToXmlDoc(response);
  }

  public abstract void decode() throws ResponseException;

  public static class Success extends EppResponseMessage {

    public void getDocument(String clTRID, ByteBuf buf) throws ResponseException {
      this.clTRID = clTRID;
      super.getDocument(buf);
    }

    @Override
    public void decode() throws ResponseException{
      verifyEppResponse(
          message,
          ImmutableList.of(
              String.format("//eppns:clTRID[.='%s']", clTRID),
            XPASS_EXPRESSION),
          true);
    }

  }

  public static class Failure extends EppResponseMessage {
    public void getDocument(String clTRID, ByteBuf buf) throws ResponseException {
      this.clTRID = clTRID;
      super.getDocument(buf);
    }

    @Override
    public void decode() throws ResponseException {
      verifyEppResponse(
          message,
          ImmutableList.of(
              String.format("//eppns:clTRID[.='%s']", clTRID),
              XFAIL_EXPRESSION),
          true);
    }

  }
}
