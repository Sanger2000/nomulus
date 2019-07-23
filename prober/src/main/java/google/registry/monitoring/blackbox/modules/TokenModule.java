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

import dagger.Module;
import dagger.Provides;
import google.registry.monitoring.blackbox.modules.EppModule.EppProtocol;
import google.registry.monitoring.blackbox.tokens.EppToken;
import google.registry.monitoring.blackbox.tokens.Token;
import google.registry.monitoring.blackbox.tokens.WebWhoisToken;
import google.registry.monitoring.blackbox.modules.WebWhoisModule.WebWhoisProtocol;
import javax.inject.Named;

@Module
public class TokenModule {

  @Provides
  @WebWhoisProtocol
  static Token provideWebToken(@WebWhoisProtocol String domainName) {
    return new WebWhoisToken(domainName);
  }

  @Provides
  @Named("Transient")
  static Token provideTransientEppToken(@Named("Epp-Tld") String tld, @Named("Epp-Host") String host) {
    return new EppToken.Transient(tld, host);
  }

  @Provides
  @Named("Persistent")
  static Token providePersistentEppToken(@Named("Epp-Tld") String tld, @Named("Epp-Host") String host) {
    return new EppToken.Persistent(tld, host);
  }


}
