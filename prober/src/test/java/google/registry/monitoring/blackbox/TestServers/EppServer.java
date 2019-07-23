package google.registry.monitoring.blackbox.TestServers;

import com.google.common.collect.ImmutableList;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;

public class EppServer extends TestServer {

  private EppServer(LocalAddress localAddress, ImmutableList<? extends ChannelHandler> handlers) {
    super(localAddress, handlers);
  }

  private EppServer(EventLoopGroup eventLoopGroup, LocalAddress localAddress, ImmutableList<? extends ChannelHandler> handlers) {
    super(eventLoopGroup, localAddress, handlers);
  }

  /**
   * Return a simple default greeting as a String.
   */
  public static String getDefaultGreeting() {
    String greeting =
        "<?xml version='1.0' encoding='UTF-8' standalone='no'?>"
            + "<epp xmlns='urn:ietf:params:xml:ns:epp-1.0'>"
            + "<greeting>"
            + "<svID>Test EPP server</svID>"
            + "<svDate>2000-06-08T22:00:00.0Z</svDate>"
            + "<svcMenu>"
            + "<version>1.0</version>"
            + "<lang>en</lang>"
            + "<lang>fr</lang>"
            + "<objURI>urn:ietf:params:xml:ns:obj1</objURI>"
            + "<objURI>urn:ietf:params:xml:ns:obj2</objURI>"
            + "<objURI>urn:ietf:params:xml:ns:obj3</objURI>"
            + "<svcExtension>"
            + "<extURI>http://custom/obj1ext-1.0</extURI>"
            + "</svcExtension>"
            + "</svcMenu>"
            + "<dcp>"
            + "<access><all/></access>"
            + "<statement>"
            + "<purpose><admin/><prov/></purpose>"
            + "<recipient><ours/><public/></recipient>"
            + "<retention><stated/></retention>"
            + "</statement>"
            + "</dcp>"
            + "</greeting>"
            + "</epp>";
    return greeting;
  }
}
