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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iosb.ilt.sta.service.TokenManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.Consts;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TokenManager authenticating as an OpenID Connect service account (OAuth 2.0
 * client credentials grant).
 *
 * The access token is fetched from the token endpoint with the client id and
 * secret, cached, and renewed shortly before it expires. Every request to the
 * SensorThings service then carries it as a bearer token. FROST-Server's
 * Keycloak provider maps the client roles found in the token to its own
 * read/create/update/delete/admin rights.
 *
 * The FROST-Client library only ships a password-grant token manager
 * ({@link de.fraunhofer.iosb.ilt.sta.service.TokenManagerOpenIDConnect}); a
 * service should not hold a user's password, hence this one.
 */
public class TokenManagerClientCredentials implements TokenManager<TokenManagerClientCredentials> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenManagerClientCredentials.class);

    /**
     * Renew the token this long before the provider says it expires, so a
     * request sent right before the expiry never travels with a dead token.
     */
    private static final long EARLY_REFRESH_MILLIS = 30_000;
    private static final long MIN_LIFETIME_MILLIS = 10_000;

    private String tokenServerUrl;
    private String clientId;
    private String clientSecret;
    private CloseableHttpClient client;

    private String accessToken;
    private long expiresAtMillis;

    @Override
    public void addAuthHeader(HttpRequest request) {
        String token = getToken();
        if (token != null) {
            request.setHeader("Authorization", "Bearer " + token);
        }
    }

    @Override
    public TokenManagerClientCredentials setHttpClient(CloseableHttpClient client) {
        this.client = client;
        return this;
    }

    @Override
    public CloseableHttpClient getHttpClient() {
        if (client == null) {
            client = HttpClients.createDefault();
        }
        return client;
    }

    /**
     * The current access token, fetched or renewed when needed. Null when the
     * provider cannot be reached or refuses the client: the request then goes
     * out without a header and the service answers 401, which is logged like
     * any other failed request.
     *
     * @return the access token, or null when none could be obtained.
     */
    public synchronized String getToken() {
        if (accessToken == null || System.currentTimeMillis() >= expiresAtMillis) {
            try {
                fetchToken();
            } catch (IOException | IllegalStateException ex) {
                LOGGER.error("Cannot fetch an access token from {} for client {}: {}", tokenServerUrl, clientId, ex.getMessage());
                LOGGER.debug("", ex);
                accessToken = null;
            }
        }
        return accessToken;
    }

    private void fetchToken() throws IOException {
        List<NameValuePair> form = new ArrayList<>();
        form.add(new BasicNameValuePair("grant_type", "client_credentials"));
        form.add(new BasicNameValuePair("client_id", clientId));
        form.add(new BasicNameValuePair("client_secret", clientSecret));
        HttpPost tokenRequest = new HttpPost(tokenServerUrl);
        tokenRequest.setEntity(new UrlEncodedFormEntity(form, Consts.UTF_8));

        try (CloseableHttpResponse response = getHttpClient().execute(tokenRequest)) {
            int status = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), Consts.UTF_8);
            if (status != 200) {
                throw new IllegalStateException("token endpoint answered " + status + ": " + body);
            }
            JsonNode tree = new ObjectMapper().readTree(body);
            JsonNode token = tree.get("access_token");
            if (token == null || token.asText().isEmpty()) {
                throw new IllegalStateException("token endpoint answered without access_token");
            }
            accessToken = token.asText();
            long expiresIn = tree.path("expires_in").asLong(60);
            long lifetime = Math.max(expiresIn * 1000 - EARLY_REFRESH_MILLIS, MIN_LIFETIME_MILLIS);
            expiresAtMillis = System.currentTimeMillis() + lifetime;
            LOGGER.debug("Fetched an access token for {} valid {} s.", clientId, lifetime / 1000);
        }
    }

    public TokenManagerClientCredentials setTokenServerUrl(String tokenServerUrl) {
        this.tokenServerUrl = tokenServerUrl;
        return this;
    }

    public TokenManagerClientCredentials setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public TokenManagerClientCredentials setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

}
