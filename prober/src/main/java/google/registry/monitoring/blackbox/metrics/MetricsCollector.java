package google.registry.monitoring.blackbox.metrics;

import com.google.common.collect.ImmutableSet;
import com.google.monitoring.metrics.CustomFitter;
import com.google.monitoring.metrics.EventMetric;
import com.google.monitoring.metrics.ExponentialFitter;
import com.google.monitoring.metrics.FibonacciFitter;
import com.google.monitoring.metrics.IncrementableMetric;
import com.google.monitoring.metrics.LabelDescriptor;
import com.google.monitoring.metrics.MetricRegistryImpl;
import google.registry.util.NonFinalForTesting;
import io.netty.handler.codec.http.FullHttpResponse;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Backend metrics instrumentation. */
@Singleton
public class MetricsCollector {

  // Maximum request size is defined in the config file, this is not realistic and we'd be out of
  // memory when the size approach 1 GB.
  private static final CustomFitter DEFAULT_SIZE_FITTER = FibonacciFitter.create(1073741824);

  // Maximum 1 hour latency, this is not specified by the spec, but given we have a one hour idle
  // timeout, it seems reasonable that maximum latency is set to 1 hour as well. If we are
  // approaching anywhere near 1 hour latency, we'd be way out of SLO anyway.
  private static final ExponentialFitter DEFAULT_LATENCY_FITTER =
      ExponentialFitter.create(22, 2, 1.0);

  private static final ImmutableSet<LabelDescriptor> LABELS =
      ImmutableSet.of(
          LabelDescriptor.create("protocol", "Name of the protocol."),
          LabelDescriptor.create());

  static final IncrementableMetric requestsCounter =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/proxy/backend/requests",
              "Total number of requests send to the backend.",
              "Requests",
              LABELS);

  static final IncrementableMetric responsesCounter =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/proxy/backend/responses",
              "Total number of responses received by the backend.",
              "Responses",
              ImmutableSet.<LabelDescriptor>builder()
                  .addAll(LABELS)
                  .add(LabelDescriptor.create("status", "HTTP status code."))
                  .build());

  static final EventMetric requestBytes =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/proxy/backend/request_bytes",
              "Size of the backend requests sent.",
              "Request Bytes",
              LABELS,
              DEFAULT_SIZE_FITTER);

  static final EventMetric responseBytes =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/proxy/backend/response_bytes",
              "Size of the backend responses received.",
              "Response Bytes",
              LABELS,
              DEFAULT_SIZE_FITTER);

  static final EventMetric latencyMs =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/proxy/backend/latency_ms",
              "Round-trip time between a request sent and its corresponding response received.",
              "Latency Milliseconds",
              LABELS,
              DEFAULT_LATENCY_FITTER);

  @Inject
  BackendMetrics() {}

  /**
   * Resets all backend metrics.
   *
   * <p>This should only used in tests to clear out states. No production code should call this
   * function.
   */
  void resetMetric() {
    requestBytes.reset();
    requestsCounter.reset();
    responseBytes.reset();
    responsesCounter.reset();
    latencyMs.reset();
  }

  @NonFinalForTesting
  public void requestSent(String protocol, String certHash, int bytes) {
    requestsCounter.increment(protocol, certHash);
    requestBytes.record(bytes, protocol, certHash);
  }

  @NonFinalForTesting
  public void responseReceived(
      String protocol, String certHash, FullHttpResponse response, long latency) {
    latencyMs.record(latency, protocol, certHash);
    responseBytes.record(response.content().readableBytes(), protocol, certHash);
    responsesCounter.increment(protocol, certHash, response.status().toString());
  }
}

