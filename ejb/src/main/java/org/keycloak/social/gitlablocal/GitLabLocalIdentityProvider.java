/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.social.gitlablocal;

import com.fasterxml.jackson.databind.JsonNode;
import org.keycloak.OAuth2Constants;
import org.keycloak.OAuthErrorException;
import org.keycloak.broker.oidc.OIDCIdentityProvider;
import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.services.ErrorResponseException;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.IOException;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 * @author <a href="mailto:3kami3@yumimix.org">Koichiro Mikami</a>
 */
public class GitLabLocalIdentityProvider extends OIDCIdentityProvider  implements SocialIdentityProvider<OIDCIdentityProviderConfig> {

	private static final String AUTH_RESOURCE = "/oauth/authorize";
	private static final String TOKEN_RESOURCE = "/oauth/token";
	private static final String PROFILE_RESOURCE = "/api/v4/user";
	private static final String API_SCOPE = "api";

	public GitLabLocalIdentityProvider(KeycloakSession session, GitLabLocalIdentityProviderConfig config) {
		super(session, config);
		final String siteUrl = ((GitLabLocalIdentityProviderConfig)getConfig()).getGitlabSiteUrl();
		config.setAuthorizationUrl(siteUrl + AUTH_RESOURCE);
		config.setTokenUrl(siteUrl + TOKEN_RESOURCE);
		config.setUserInfoUrl(siteUrl + PROFILE_RESOURCE);

		String defaultScope = config.getDefaultScope();

		if (defaultScope.equals(SCOPE_OPENID)) {
			config.setDefaultScope((API_SCOPE + " " + defaultScope).trim());
		}
	}

	protected String getUsernameFromUserInfo(JsonNode userInfo) {
		return getJsonProperty(userInfo, "username");
	}

	protected String getusernameClaimNameForIdToken() {
		return IDToken.NICKNAME;
	}

	@Override
	protected boolean supportsExternalExchange() {
		return true;
	}

	@Override
	protected String getProfileEndpointForValidation(EventBuilder event) {
		return getUserInfoUrl();
	}

	@Override
	public boolean isIssuer(String issuer, MultivaluedMap<String, String> params) {
		String requestedIssuer = params.getFirst(OAuth2Constants.SUBJECT_ISSUER);
		if (requestedIssuer == null) requestedIssuer = issuer;
		return requestedIssuer.equals(getConfig().getAlias());
	}


	@Override
	protected BrokeredIdentityContext exchangeExternalImpl(EventBuilder event, MultivaluedMap<String, String> params) {
		return exchangeExternalUserInfoValidationOnly(event, params);
	}

	@Override
	protected BrokeredIdentityContext extractIdentityFromProfile(EventBuilder event, JsonNode profile) {
		String id = getJsonProperty(profile, "id");
		if (id == null) {
			event.detail(Details.REASON, "id claim is null from user info json");
			event.error(Errors.INVALID_TOKEN);
			throw new ErrorResponseException(OAuthErrorException.INVALID_TOKEN, "invalid token", Response.Status.BAD_REQUEST);
		}
		return gitlablocalExtractFromProfile(profile);
	}

	private BrokeredIdentityContext gitlablocalExtractFromProfile(JsonNode profile) {
		String id = getJsonProperty(profile, "id");
		BrokeredIdentityContext identity = new BrokeredIdentityContext(id);

		String name = getJsonProperty(profile, "name");
		String preferredUsername = getJsonProperty(profile, "username");
		String email = getJsonProperty(profile, "email");
		AbstractJsonUserAttributeMapper.storeUserProfileForMapper(identity, profile, getConfig().getAlias());

		identity.setId(id);
		identity.setName(name);
		identity.setEmail(email);

		identity.setBrokerUserId(getConfig().getAlias() + "." + id);

		if (preferredUsername == null) {
			preferredUsername = email;
		}

		if (preferredUsername == null) {
			preferredUsername = id;
		}

		identity.setUsername(preferredUsername);
		return identity;
	}


	protected BrokeredIdentityContext extractIdentity(AccessTokenResponse tokenResponse, String accessToken, JsonWebToken idToken) throws IOException {

		SimpleHttp.Response response = null;
		int status = 0;

		for (int i = 0; i < 10; i++) {
			try {
				String userInfoUrl = getUserInfoUrl();
				response = SimpleHttp.doGet(userInfoUrl, session)
						.header("Authorization", "Bearer " + accessToken).asResponse();
				status = response.getStatus();
			} catch (IOException e) {
				logger.debug("Failed to invoke user info for external exchange", e);
			}
			if (status == 200) break;
			response.close();
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		if (status != 200) {
			logger.debug("Failed to invoke user info status: " + status);
			throw new IdentityBrokerException("Gitlab user info call failure");
		}
		JsonNode profile = null;
		try {
			profile = response.asJson();
		} catch (IOException e) {
			throw new IdentityBrokerException("Gitlab user info call failure");
		}
		String id = getJsonProperty(profile, "id");
		if (id == null) {
			throw new IdentityBrokerException("Gitlab id claim is null from user info json");
		}
		BrokeredIdentityContext identity = gitlablocalExtractFromProfile(profile);
		identity.getContextData().put(FEDERATED_ACCESS_TOKEN_RESPONSE, tokenResponse);
		identity.getContextData().put(VALIDATED_ID_TOKEN, idToken);
		processAccessTokenResponse(identity, tokenResponse);

		return identity;
	}






}
