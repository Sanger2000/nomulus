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

package google.registry.monitoring.blackbox;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import io.netty.channel.local.LocalAddress;
import io.netty.util.AttributeKey;
import io.netty.channel.ChannelHandler;
import javax.inject.Provider;

/**
 * Protocol Class packages all static variables necessary for a certain type of connection
 * Both the host and the path can be changed for the same protocol
 * Mainly packages the handlers necessary for the requisite channel pipeline
 */
@AutoValue
public abstract class Protocol {

  public final static AttributeKey<Protocol> PROTOCOL_KEY = AttributeKey.valueOf("PROTOCOL_KEY");

  /**
   * Default names associated with each protocol
   */
  final static String EPP_PROTOCOL_NAME = "EPP";
  final static String DNS_PROTOCOL_NAME = "DNS";
  final static String WHOIS_PROTOCOL_NAME =  "WHOIS";
  final static String RDAP_PROTOCOL_NAME = "RDAP";

  private String host;
  private LocalAddress address;
  private String path = "";
  private ProbingAction probingAction;

  /** Setter method for Protocol's host*/
  public Protocol host(String host) {
    this.host = host;
    return this;
  }

  /** Getter method for Protocol's host*/
  public String host() {
    return host;
  }

  /** Setter method for Protocol's path*/
  public Protocol path(String path) {
    this.path = path;
    return this;
  }

  /** Getter method for Protocol's path*/
  public String path() {
    return path;
  }

  /** Setter method for Protocol's ProbingAction parent*/
  public Protocol probingAction(ProbingAction probingAction) {
    this.probingAction = probingAction;
    return this;
  }

  /** Getter method for Protocol's path. ONLY FOR TESTING*/
  public LocalAddress address() {
    return address;
  }

  /** Setter method for Protocol's ProbingAction parent. ONLY FOR TESTING*/
  public Protocol address(LocalAddress address) {
    this.address = address;
    return this;
  }

  /** Getter method for Protocol's path*/
  public ProbingAction probingAction() {
    return probingAction;
  }

  /** If connection associated with Protocol is persistent, which is only EPP */
  boolean persistentConnection() {
    return name().equals(EPP_PROTOCOL_NAME);
  }

  /** Protocol Name */
  abstract String name();

  /** Port to bind to at remote host */
  public abstract int port();

  /** The {@link ChannelHandler} providers to use for the protocol, in order. */
  abstract ImmutableList<Provider<? extends ChannelHandler>> handlerProviders();


  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_Protocol.Builder();
  }

  @AutoValue.Builder
  public static abstract class Builder {

    public abstract Builder name(String value);

    public abstract Builder port(int num);

    public abstract Builder handlerProviders(ImmutableList<Provider<? extends ChannelHandler>> providers);

    public abstract Protocol build();
  }
}

