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

import static google.registry.monitoring.blackbox.ProberConfig.getProberConfig;

import com.beust.jcommander.Parameter;
import com.google.api.services.cloudkms.v1.CloudKMS;
import com.google.api.services.cloudkms.v1.model.DecryptRequest;
import com.google.api.services.storage.Storage;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import google.registry.monitoring.blackbox.ProberConfig.Environment;
import google.registry.monitoring.blackbox.modules.EppModule.EppProtocol;
import google.registry.monitoring.blackbox.tokens.Token;
import google.registry.monitoring.blackbox.connection.Protocol;
import google.registry.monitoring.blackbox.modules.EppModule;
import google.registry.monitoring.blackbox.modules.TokenModule;
import google.registry.monitoring.blackbox.modules.WebWhoisModule;
import google.registry.monitoring.blackbox.modules.WebWhoisModule.HttpWhoisProtocol;
import google.registry.monitoring.blackbox.modules.WebWhoisModule.HttpsWhoisProtocol;
import google.registry.monitoring.blackbox.modules.WebWhoisModule.WebWhoisProtocol;
import google.registry.util.GoogleCredentialsBundle;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * {@link Dagger} main module, which Provides {@link ProbingSequences} and houses {@link ProberComponent}
 *
 * <p>Provides</p>
 */
@Module
public class ProberModule {
  private final int httpWhoIsPort = 80;
  private final int httpsWhoIsPort = 443;

  @Parameter(names = "--env", description = "Environment to run the proxy in")
  private Environment env = Environment.LOCAL;

  @Singleton
  @Provides
  static GoogleCredentialsBundle provideCredential(ProberConfig config) {
    try {
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
      if (credentials.createScopedRequired()) {
        credentials = credentials.createScoped(config.gcpScopes);
      }
      return GoogleCredentialsBundle.create(credentials);
    } catch (IOException e) {
      throw new RuntimeException("Unable to obtain OAuth2 credential.", e);
    }
  }
  @Singleton
  @Provides
  static CloudKMS provideCloudKms(GoogleCredentialsBundle credentialsBundle, ProberConfig config) {
    return new CloudKMS.Builder(
        credentialsBundle.getHttpTransport(),
        credentialsBundle.getJsonFactory(),
        credentialsBundle.getHttpRequestInitializer())
        .setApplicationName(config.projectId)
        .build();
  }

  @Singleton
  @Provides
  static Storage provideStorage(GoogleCredentialsBundle credentialsBundle, ProberConfig config) {
    return new Storage.Builder(
        credentialsBundle.getHttpTransport(),
        credentialsBundle.getJsonFactory(),
        credentialsBundle.getHttpRequestInitializer())
        .setApplicationName(config.projectId)
        .build();
  }

  // This binding should not be used directly. Use those provided in CertificateModule instead.
  @Provides
  @Named("encryptedPemBytes")
  static byte[] provideEncryptedPemBytes(Storage storage, ProberConfig config) {
    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      storage
          .objects()
          .get(config.gcs.bucket, config.gcs.sslPemFilename)
          .executeMediaAndDownloadTo(outputStream);
      return Base64.getMimeDecoder().decode(outputStream.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException(
          String.format(
              "Error reading encrypted PEM file %s from GCS bucket %s",
              config.gcs.sslPemFilename, config.gcs.bucket),
          e);
    }
  }

  // This binding should not be used directly. Use those provided in CertificateModule instead.
  @Provides
  @Named("pemBytes")
  static byte[] providePemBytes(
      CloudKMS cloudKms, @Named("encryptedPemBytes") byte[] encryptedPemBytes, ProberConfig config) {
    String cryptoKeyUrl =
        String.format(
            "projects/%s/locations/%s/keyRings/%s/cryptoKeys/%s",
            config.projectId, config.kms.location, config.kms.keyRing, config.kms.cryptoKey);
    try {
      DecryptRequest decryptRequest = new DecryptRequest().encodeCiphertext(encryptedPemBytes);
      return cloudKms
          .projects()
          .locations()
          .keyRings()
          .cryptoKeys()
          .decrypt(cryptoKeyUrl, decryptRequest)
          .execute()
          .decodePlaintext();
    } catch (IOException e) {
      throw new RuntimeException(
          String.format("PEM file decryption failed using CryptoKey: %s", cryptoKeyUrl), e);
    }
  }
  @Provides
  @Singleton
  EventLoopGroup provideEventLoopGroup() {
    return new NioEventLoopGroup();
  }

  @Provides
  @HttpWhoisProtocol
  ProbingSequence<NioSocketChannel> provideHttpWhoisSequence(
      @HttpWhoisProtocol ProbingStep<NioSocketChannel> probingStep,
      EventLoopGroup eventLoopGroup) {
    return new ProbingSequence.Builder<NioSocketChannel>()
        .setClass(NioSocketChannel.class)
        .addStep(probingStep)
        .makeFirstRepeated()
        .eventLoopGroup(eventLoopGroup)
        .build();
  }

  @Provides
  @HttpsWhoisProtocol
  ProbingSequence<NioSocketChannel> provideHttpsWhoisSequence(
      @HttpsWhoisProtocol ProbingStep<NioSocketChannel> probingStep,
      EventLoopGroup eventLoopGroup) {
    return new ProbingSequence.Builder<NioSocketChannel>()
        .setClass(NioSocketChannel.class)
        .addStep(probingStep)
        .makeFirstRepeated()
        .eventLoopGroup(eventLoopGroup)
        .build();
  }

  @Provides
  @EppProtocol
  ProbingSequence<NioSocketChannel> provideEppSequence(
      @HttpsWhoisProtocol ProbingStep<NioSocketChannel> probingStep,
      EventLoopGroup eventLoopGroup) {
    return new ProbingSequence.Builder<NioSocketChannel>()
        .setClass(NioSocketChannel.class)
        .addStep(probingStep)
        .makeFirstRepeated()
        .eventLoopGroup(eventLoopGroup)
        .build();
  }
  @Provides
  @HttpWhoisProtocol
  int provideHttpWhoisPort() {
    return httpWhoIsPort;
  }

  @Provides
  @HttpsWhoisProtocol
  int provideHttpsWhoisPort() {
    return httpsWhoIsPort;
  }

  @Provides
  ImmutableMap<Integer, Protocol> providePortToProtocolMap(
      Set<Protocol> protocolSet) {
    return Maps.uniqueIndex(protocolSet, Protocol::port);
  }

  @Singleton
  @Provides
  ProberConfig provideProxyConfig(Environment env) {
    return getProberConfig(env);
  }



  @Singleton
  @Component(
      modules = {
          ProberModule.class,
          WebWhoisModule.class,
          EppModule.class,
          TokenModule.class
      })
  public interface ProberComponent {

    @HttpWhoisProtocol
    public ProbingSequence<NioSocketChannel> provideHttpWhoisSequence();

    @HttpsWhoisProtocol
    public ProbingSequence<NioSocketChannel> provideHttpsWhoisSequence();

    public ImmutableMap<Integer, Protocol> providePortToProtocolMap();

    @WebWhoisProtocol
    public Token provideWebWhoisToken();

  }
}
