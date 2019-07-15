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

import google.registry.monitoring.blackbox.messages.EppClientException;
import google.registry.monitoring.blackbox.messages.OutboundMessageType;
import io.netty.channel.Channel;
import java.io.IOException;

public abstract class Token {

  private static final int MAX_DOMAIN_PART_LENGTH = 50;
  private static int clientIdSuffix = 0;
  protected String localHostname;
  protected String domainName;
  protected Channel channel;

  public abstract Token next();
  public abstract OutboundMessageType modifyMessage(OutboundMessageType message)
      throws IOException, EppClientException;
  public abstract String getHost();

  public void channel(Channel channel) {
    this.channel = channel;
  }
  public Channel channel() {
    return this.channel;
  }


  /**
   * Return a unique string usable as an EPP client transaction ID.
   *
   * <p><b>Warning:</b> The prober cleanup servlet relies on the timestamp being in the third
   * position when splitting on dashes. Do not change this format without updating that code as
   * well.
   */
  protected synchronized String getNewTRID() {
    return String.format("prober-%s-%d-%d",
        localHostname,
        System.currentTimeMillis(),
        clientIdSuffix++);
  }

  /**
   * Return a fully qualified domain label to use, derived from the client transaction ID.
   */
  protected static synchronized String newDomainName(String clTRID) {
    String sld;
    // not sure if the local hostname will stick to RFC validity rules
    if (clTRID.length() > MAX_DOMAIN_PART_LENGTH) {
      sld = clTRID.substring(clTRID.length() - MAX_DOMAIN_PART_LENGTH);
    } else {
      sld = clTRID;
    }
    //insert top level domain here
    return String.format("%s.%s", sld, "app");
  }


}
