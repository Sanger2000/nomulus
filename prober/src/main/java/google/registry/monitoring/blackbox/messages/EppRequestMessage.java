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

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Internal;
import google.registry.monitoring.blackbox.exceptions.EppClientException;
import google.registry.monitoring.blackbox.exceptions.InternalException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.util.Map;

public abstract class EppRequestMessage extends EppMessage implements OutboundMessageType {

  private final static String DOMAIN_KEY = "//domainns:name";
  private final static String CLIENT_ID_KEY = "//eppns:clID";
  private final static String CLIENT_PASSWORD_KEY = "//eppns:pw";
  private final static String CLIENT_TRID_KEY = "//eppns:clTRID";

  protected ImmutableMap<String, String> replacements;
  protected String clTRID;
  private String template;


  public EppRequestMessage modifyMessage(String clTRID, String newDomain) throws InternalException {
    this.clTRID = clTRID;
    Map<String, String> nextArguments = ImmutableMap.<String, String>builder()
        .putAll(replacements)
        .put(DOMAIN_KEY, newDomain)
        .put(CLIENT_TRID_KEY, clTRID)
        .build();
    try {
      message = getEppDocFromTemplate(template, nextArguments);
    } catch (IOException | EppClientException e) {
      throw new InternalException(e);
    }
    return this;
  }

  public String getClTRID() {
    return clTRID;
  }


  private EppRequestMessage(String template, ImmutableMap<String, String> replacements) {
    this.template = template;
    this.replacements = replacements;
  }

  public ByteBuf bytes() throws EppClientException{
    byte[] bytestream = xmlDocToByteArray(message);
    int capacity = HEADER_LENGTH + bytestream.length;

    ByteBuf buf = Unpooled.buffer(capacity);

    buf.writeInt(capacity);
    buf.writeBytes(bytestream);

    return buf;
  }

  public static class HELLO extends EppRequestMessage {
    private static final String template = "hello.xml";
    public HELLO() {
      super(
          template,
          ImmutableMap.of()
      );
    }

    @Override
    public EppRequestMessage modifyMessage(String clTRID, String newDomain) throws InternalException {
      this.clTRID = clTRID;
      try {
        message = getEppDocFromTemplate(template, ImmutableMap.of(CLIENT_TRID_KEY, clTRID));
      } catch (IOException | EppClientException e) {
        throw new InternalException(e);
      }
      return this;
    }
  }

  public static class LOGIN extends EppRequestMessage {
    private static final String template = "login.xml";

    public LOGIN(String eppClientId, String eppClientPassword) {
      super(
          template,
          ImmutableMap.of(
              CLIENT_ID_KEY, eppClientId,
              CLIENT_PASSWORD_KEY, eppClientPassword
          )
      );
    }

    @Override
    public EppRequestMessage modifyMessage(String clTRID, String newDomain) throws InternalException {
      this.clTRID = clTRID;
      Map<String, String> nextArguments = ImmutableMap.<String, String>builder()
          .putAll(replacements)
          .put(CLIENT_TRID_KEY, clTRID)
          .build();
      try {
        message = getEppDocFromTemplate(template, nextArguments);
      } catch (IOException | EppClientException e) {
        throw new InternalException(e);
      }
      return this;
    }
  }

  public static class CHECK extends EppRequestMessage {
    private static final String template = "check.xml";

    public CHECK(String clTRID) {
      super(
          template,
          ImmutableMap.of()
      );
    }
  }

  public static class CLAIMSCHECK extends EppRequestMessage {
    private static final String template = "claimscheck.xml";

    public CLAIMSCHECK(String clTRID) {
      super(
          template,
          ImmutableMap.of()
      );
    }
  }

  public static class CREATE extends EppRequestMessage {
    private static final String template = "create.xml";

    public CREATE(String clTRID) {
      super(
          template,
          ImmutableMap.of()
      );
    }
  }

  public static class DELETE extends EppRequestMessage {
    private static final String template = "delete.xml";
    public DELETE(String clTRID) {
      super(
          template,
          ImmutableMap.of()
      );
    }
  }

  public static class LOGOUT extends EppRequestMessage {
    private static final String template = "logout.xml";

    public LOGOUT(String clTRID) {
      super(
          template,
          ImmutableMap.of()
      );
    }

    @Override
    public EppRequestMessage modifyMessage(String clTRID, String newDomain) throws InternalException {
      this.clTRID = clTRID;
      try {
        message = getEppDocFromTemplate(template, ImmutableMap.of(CLIENT_TRID_KEY, clTRID));
      } catch (IOException | EppClientException e) {
        throw new InternalException(e);
      }
      return this;
    }
  }


}
