package com.frog.auth.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.Set;

/**
 *
 *
 * @author Deng
 * createData 2025/11/13 10:46
 * @version 1.0
 */
public interface ICustomAuthorizationService {
    OAuth2Authorization createAuthorization(RegisteredClient client,
                                            Authentication principal,
                                            Set<String> authorizedScopes);
}
