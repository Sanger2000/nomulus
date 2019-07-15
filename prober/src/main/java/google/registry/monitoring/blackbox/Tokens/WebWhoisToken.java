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

import google.registry.monitoring.blackbox.TokenModule.WebWhoIs;
import google.registry.monitoring.blackbox.messages.HttpRequestMessage;
import google.registry.monitoring.blackbox.messages.OutboundMessageType;
import javax.inject.Inject;

public class WebWhoisToken extends Token {
  private static final String prefix = "whois.nic.";
  private static final String name = "app";
  private String host;

  @Inject
  public WebWhoisToken(@WebWhoIs String domainName) {
    host = prefix + domainName;
  }

  @Override
  public Token next() {
    return new WebWhoisToken(name);
  }

  @Override
  public OutboundMessageType modifyMessage(OutboundMessageType original) {
    HttpRequestMessage request = (HttpRequestMessage) original;
    request.headers().set("host", host);

    return request;

  }
  @Override
  public String getHost() {
    return host;
  }


}

