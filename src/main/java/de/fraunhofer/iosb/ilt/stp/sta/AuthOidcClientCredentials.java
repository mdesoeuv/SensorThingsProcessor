/*
 * Copyright (C) 2018 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
 * Karlsruhe, Germany.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.fraunhofer.iosb.ilt.stp.sta;

import de.fraunhofer.iosb.ilt.configurable.AnnotatedConfigurable;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableField;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorBoolean;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorPassword;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.LoggerFactory;

/**
 * Authenticates against the SensorThings service as an OpenID Connect
 * service account: a bearer token obtained with the client credentials grant
 * from the provider's token endpoint (Keycloak, or any OAuth 2.0 provider).
 *
 * Configuration:
 * <pre>
 * "authMethod": {
 *   "className": "de.fraunhofer.iosb.ilt.stp.sta.AuthOidcClientCredentials",
 *   "classConfig": {
 *     "tokenUrl": "https://keycloak.example.org/realms/frost/protocol/openid-connect/token",
 *     "clientId": "frost-processor",
 *     "clientSecret": "...",
 *     "ignoreSslErrors": false
 *   }
 * }
 * </pre>
 *
 * Only the HTTP requests carry the token. The MQTT connection stays
 * anonymous, which FROST-Server allows for subscriptions when
 * {@code auth.allowAnonymousRead} is on.
 */
public class AuthOidcClientCredentials implements AnnotatedConfigurable<Void, Void>, AuthMethod {

    /**
     * The logger for this class.
     */
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AuthOidcClientCredentials.class);

    @ConfigurableField(editor = EditorString.class,
            label = "Token URL",
            description = "The token endpoint of the OpenID Connect provider (…/protocol/openid-connect/token on Keycloak)")
    @EditorString.EdOptsString(dflt = "http://localhost:8180/realms/frost/protocol/openid-connect/token")
    private String tokenUrl;

    @ConfigurableField(editor = EditorString.class,
            label = "Client ID",
            description = "The client whose service account the processor authenticates as")
    @EditorString.EdOptsString()
    private String clientId;

    @ConfigurableField(editor = EditorPassword.class,
            label = "Client Secret",
            description = "The secret of that client")
    @EditorPassword.EdOptsPassword()
    private String clientSecret;

    @ConfigurableField(editor = EditorBoolean.class,
            label = "IgnoreSslErrors",
            description = "Ignore SSL certificate errors. This is a bad idea unless you know what you are doing.")
    @EditorBoolean.EdOptsBool()
    private boolean ignoreSslErrors;

    @Override
    public void setAuth(SensorThingsService service) {
        try {
            if (ignoreSslErrors) {
                SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(new SSLContextBuilder().loadTrustMaterial((X509Certificate[] chain, String authType) -> true).build());
                service.getClientBuilder().setSSLSocketFactory(sslsf);
                service.rebuildHttpClient();
            }
            // The service hands its HTTP client to the token manager, so the
            // token requests share the connection settings of the data ones.
            service.setTokenManager(new TokenManagerClientCredentials()
                    .setTokenServerUrl(tokenUrl)
                    .setClientId(clientId)
                    .setClientSecret(clientSecret));
            LOGGER.info("Authenticating on {} as service account {} through {}.", service.getEndpoint(), clientId, tokenUrl);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException ex) {
            LOGGER.error("Failed to initialise OpenID Connect auth.", ex);
        }
    }

}
