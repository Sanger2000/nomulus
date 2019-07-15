package google.registry.monitoring.blackbox.messages;

/**
 * Base exception class for all EPP client connection failures
 */
public class EppClientException extends Exception {

  public EppClientException(String msg) {
    super(msg);
  }

  public EppClientException(Throwable e) {
    super(e);
  }
}
