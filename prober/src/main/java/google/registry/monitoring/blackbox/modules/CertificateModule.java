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

package google.registry.monitoring.blackbox.modules;

import static google.registry.util.ResourceUtils.readResourceBytes;
import static google.registry.util.ResourceUtils.readResourceUtf8;

import com.google.common.flogger.FluentLogger;
import dagger.Module;
import dagger.Provides;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Dagger module that provides bindings needed to inject server certificate chain and private key.
 *
 * <p>The production certificates and private key are stored in a .pem file that is encrypted by
 * Cloud KMS. The .pem file can be generated by concatenating the .crt certificate files on the
 * chain and the .key private file.
 *
 * <p>The production certificates in the .pem file must be stored in order, where the next
 * certificate's subject is the previous certificate's issuer.
 *
 * <p>When running the proxy locally or in test, a self signed certificate is used.
 *
 * @see <a href="https://cloud.google.com/kms/">Cloud Key Management Service</a>
 */
@Module
public class CertificateModule {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static InputStream readResource(String filename)
      throws IOException {
    return readResourceBytes(CertificateModule.class, filename).openStream();
  }

  @Singleton
  @Provides
  @Named("keystore")
  static String keystorePasswordProvider() {
    return readResourceUtf8(CertificateModule.class, "secrets/keystore_password.txt");
  }


  @Singleton
  @Provides
  static PrivateKey providePrivateKey(@Named("keystore") Provider<String> passwordProvider) {
    try {
      InputStream inStream = readResource("secrets/prober-client-tls-sandbox.p12");

      KeyStore ks = KeyStore.getInstance("PKCS12");
      ks.load(inStream, passwordProvider.get().toCharArray());

      String alias = ks.aliases().nextElement();
      return (PrivateKey) ks.getKey(alias, "passphrase".toCharArray());
    } catch (IOException | GeneralSecurityException e) {
      return PrivateKey.;
    }
  }

  @Singleton
  @Provides
  static X509Certificate[] provideCertificates(
      @Named("keystore") Provider<String> passwordProvider) {
    try {
      InputStream inStream = readResource("secrets/prober-client-tls-sandbox.p12");

      KeyStore ks = KeyStore.getInstance("PKCS12");
      ks.load(inStream, passwordProvider.get().toCharArray());

      String alias = ks.aliases().nextElement();
      return new X509Certificate[]{(X509Certificate) ks.getCertificate(alias)};
    } catch (Exception e) {
      logger.atWarning().withCause(e).log();
      return null;
    }
  }
}
