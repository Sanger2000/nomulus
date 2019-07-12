package google.registry.monitoring.blackbox;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import google.registry.monitoring.blackbox.Tokens.Token;
import google.registry.monitoring.blackbox.Tokens.WebWhoisToken;
import google.registry.monitoring.blackbox.messages.HttpRequestMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TokenTest {


  private static String PREFIX = "whois.nic.";
  private static String TEST_STARTER = "starter";
  private static String TEST_DOMAIN = "test";

  public Token webToken = new WebWhoisToken(TEST_DOMAIN);

  @Test
  public void testWebToken_MessageModificationSuccess() {
    HttpRequestMessage message = new HttpRequestMessage(HttpVersion.HTTP_1_1, HttpMethod.GET, "");
    message.headers().set("host", TEST_STARTER);

    HttpRequestMessage secondMessage = (HttpRequestMessage) webToken.modifyMessage(message);

    assertThat(secondMessage.headers().get("host")).isEqualTo(PREFIX+TEST_DOMAIN);

  }

}
