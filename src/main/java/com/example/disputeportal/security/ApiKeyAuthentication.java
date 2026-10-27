package com.example.disputeportal.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * An authenticated API clients authorities are its scopes, each prefixed with
 * "SCOPE_" (for example SCOPE_transactions:load), which the access rules in
 * SecurityConfig and the @PreAuthorize checks look for.
 */
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final String clientName;

    /**
     * @param clientName the name of the API client whose key was verified
     * @param scopes what that client is allowed to do (see {@link ApiScopes})
     */
    public ApiKeyAuthentication(String clientName, Collection<String> scopes) {
        super(scopes.stream().map(scope -> new SimpleGrantedAuthority(ApiScopes.authority(scope))).toList());
        this.clientName = clientName;
        // Safe to mark as authenticated: this object is only created after the key has been verified.
        setAuthenticated(true);
    }

    /**
     * No credentials are kept; the key is discarded as soon as it has been
     * checked.
     */
    @Override
    public Object getCredentials() {
        return null;   // the key is never kept after it has been checked
    }

    /**
     * The principal is simply the client's name (shown in logs and passed to
     * the services).
     */
    @Override
    public Object getPrincipal() {
        return clientName;
    }
}
