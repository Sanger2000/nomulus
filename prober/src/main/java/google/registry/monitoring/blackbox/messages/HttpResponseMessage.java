package google.registry.monitoring.blackbox.messages;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class HttpResponseMessage extends DefaultFullHttpResponse implements InboundMarker {

  public HttpResponseMessage(HttpVersion version, HttpResponseStatus status) {
    super(version, status);
  }

  public HttpResponseMessage(HttpVersion version, HttpResponseStatus status, ByteBuf content) {
    super(version, status, content);
  }

  public HttpResponseMessage(HttpVersion version, HttpResponseStatus status, ByteBuf content, boolean validateHeaders) {
    super(version, status, content, validateHeaders);
  }



}
