// Copyright 2019 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.monitoring.blackbox.messages;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

/**
 * {@link OutboundMessageType} instance which functions identically to {@link DefaultFullHttpRequest}
 * (but needs to implement {@link OutboundMessageType})
 *
 * <p>Uses identical constructors to {@link DefaultFullHttpRequest} and allows for conversion
 * from {@link FullHttpRequest} to its type</p>
 */
public class HttpRequestMessage extends DefaultFullHttpRequest implements OutboundMessageType {

  public HttpRequestMessage(HttpVersion httpVersion, HttpMethod method, String uri) {
    super(httpVersion, method, uri);
  }
  public HttpRequestMessage(HttpVersion httpVersion, HttpMethod method, String uri, ByteBuf content) {
    super(httpVersion, method, uri, content);
  }

  @Override
  public HttpRequestMessage setUri(String path) {
    super.setUri(path);
    return this;
  }

  /** Converts from {@link FullHttpRequest} to type {@link HttpRequestMessage} */
  public static HttpRequestMessage fromRequest(FullHttpRequest request) {
    HttpRequestMessage finalRequest;
    ByteBuf buf = request.content();

    //creates message based on content found in original request
    if (buf == null)
      finalRequest = new HttpRequestMessage(HttpVersion.HTTP_1_1, request.method(), request.uri());
    else
      finalRequest =  new HttpRequestMessage(HttpVersion.HTTP_1_1, request.method(), request.uri(), buf);


    //stores headers from request in finalRequest
    request.headers().forEach((pair) -> finalRequest.headers().set(pair.getKey(), pair.getValue()));

    return finalRequest;

  }
}
