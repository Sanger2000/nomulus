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

import static google.registry.util.ResourceUtils.readResourceUtf8;
import static google.registry.util.YamlUtils.getConfigSettings;

import com.google.common.base.Ascii;
import java.util.List;

/** The POJO that YAML config files are deserialized into. */
public class ProberConfig {

  public enum Environment {
    PRODUCTION,
    PRODUCTION_CANARY,
    SANDBOX,
    SANDBOX_CANARY,
    CRASH,
    CRASH_CANARY,
    ALPHA,
    LOCAL,
  }

  private static final String DEFAULT_CONFIG = "config/default-config.yaml";
  private static final String CUSTOM_CONFIG_FORMATTER = "config/proxy-config-%s.yaml";

  public String projectId;
  public List<String> gcpScopes;
  public int accessTokenRefreshBeforeExpirationSeconds;
  public int serverCertificateCacheSeconds;
  public Gcs gcs;
  public Kms kms;
  public Epp epp;
  public Whois whois;
  public WebWhois webWhois;
  public Metrics metrics;

  /** Configuration options that apply to GCS. */
  public static class Gcs {
    public String bucket;
    public String sslPemFilename;
  }

  /** Configuration options that apply to Cloud KMS. */
  public static class Kms {
    public String location;
    public String keyRing;
    public String cryptoKey;
  }

  /** Configuration options that apply to EPP protocol. */
  public static class Epp {
    public int port;
    public String host;
    public int readTimeoutSeconds;
  }

  /** Configuration options that apply to WHOIS protocol. */
  public static class Whois {
    public int port;
    public String host;
    public String path;
    public int maxMessageLengthBytes;
  }

  /** Configuration options that apply to web WHOIS redirects. */
  public static class WebWhois {
    public int httpPort;
    public int httpsPort;
    public String[] domains;
  }

  /** Configuration options that apply to Stackdriver monitoring metrics. */
  public static class Metrics {
    public int stackdriverMaxQps;
    public int stackdriverMaxPointsPerRequest;
    public int writeIntervalSeconds;
  }

  /** Configuration options that apply to quota management. */
  public static class Quota {

    public int delaySeconds;
  }

  static ProberConfig getProberConfig(Environment env) {
    String defaultYaml = readResourceUtf8(ProberConfig.class, DEFAULT_CONFIG);
    String customYaml =
        readResourceUtf8(
            ProberConfig.class,
            String.format(
                CUSTOM_CONFIG_FORMATTER, Ascii.toLowerCase(env.name()).replace("_", "-")));
    return getConfigSettings(defaultYaml, customYaml, ProberConfig.class);
  }
}
