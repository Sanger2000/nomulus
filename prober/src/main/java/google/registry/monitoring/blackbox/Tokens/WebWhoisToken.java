// Copyright 2018 The Nomulus Authors. All Rights Reserved.
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

package google.registry.monitoring.blackbox.Tokens;

import google.registry.monitoring.blackbox.ProbingStep;
import google.registry.monitoring.blackbox.Protocol;
import google.registry.monitoring.blackbox.TokenModule.WebWhoIs;
import google.registry.monitoring.blackbox.messages.HttpRequestMessage;
import google.registry.monitoring.blackbox.messages.OutboundMarker;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import javax.inject.Inject;

public class WebWhoisToken extends Token {
  private static final String name = "app";
  private String host;

  @Inject
  public WebWhoisToken(@WebWhoIs String domainName) {
    this.domainName = domainName;
  }

  @Override
  public Token next(ProbingStep<? extends AbstractChannel> nextAction) {
    return new WebWhoisToken(name);
  }

  @Override
  public OutboundMarker modifyMessage(OutboundMarker original) {
    HttpRequestMessage request = (HttpRequestMessage) original;
    String originalHost = request.headers().get("host");
    host = originalHost.substring(0, originalHost.lastIndexOf('.')+1) + domainName;
    request.headers().set("host", host);

    return request;

  }
  @Override
  public Protocol modifyProtocol(Protocol original) {
    return original.host(host);
  }


  @Override
  public Channel channel() { return null; }

}

