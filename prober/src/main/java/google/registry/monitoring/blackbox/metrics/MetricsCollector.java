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


package google.registry.monitoring.blackbox.metrics;

import com.google.common.collect.ImmutableSet;
import com.google.monitoring.metrics.EventMetric;
import com.google.monitoring.metrics.ExponentialFitter;
import com.google.monitoring.metrics.IncrementableMetric;
import com.google.monitoring.metrics.LabelDescriptor;
import com.google.monitoring.metrics.MetricRegistryImpl;
import google.registry.monitoring.blackbox.handlers.ActionHandler.ResponseType;
import google.registry.util.NonFinalForTesting;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Backend metrics instrumentation. */
@Singleton
public class MetricsCollector {

  // Maximum 1 hour latency, this is not specified by the spec, but given we have a one hour idle
  // timeout, it seems reasonable that maximum latency is set to 1 hour as well. If we are
  // approaching anywhere near 1 hour latency, we'd be way out of SLO anyway.
  private static final ExponentialFitter DEFAULT_LATENCY_FITTER =
      ExponentialFitter.create(22, 2, 1.0);

  private static final ImmutableSet<LabelDescriptor> LABELS =
      ImmutableSet.of(
          LabelDescriptor.create("protocol", "Name of the protocol."),
          LabelDescriptor.create("action", "type of action"),
          LabelDescriptor.create("responseType", "Status of action performed"));

  static final IncrementableMetric responsesCounter =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/prober/responses",
              "Total number of responses received by the backend.",
              "Responses",
              LABELS);

  static final EventMetric latencyMs =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/prober/latency_specific_ms",
              "Round-trip time between a request sent and its corresponding response received.",
              "Latency Milliseconds",
              LABELS,
              DEFAULT_LATENCY_FITTER);


  @Inject
  MetricsCollector() {}

  /**
   * Resets all backend metrics.
   *
   * <p>This should only used in tests to clear out states. No production code should call this
   * function.
   */
  void resetMetric() {
    responsesCounter.reset();
    latencyMs.reset();
  }

  @NonFinalForTesting
  public void recordResult(String protocolName, String actionName, ResponseType response, long latency) {
    latencyMs.record(latency, protocolName, actionName, response.name());
    responsesCounter.increment(protocolName, actionName, response.name());
  }
}


