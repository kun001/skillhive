package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.AuthMeResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Builds the current-user API response with account capabilities derived from
 * authoritative backend state.
 */
@Service
public class AuthMeResponseAssembler {

    private final LocalCredentialRepository localCredentialRepository;

    public AuthMeResponseAssembler(LocalCredentialRepository localCredentialRepository) {
        this.localCredentialRepository = localCredentialRepository;
    }

    public AuthMeResponse from(PlatformPrincipal principal) {
        return AuthMeResponse.from(
                principal,
                localCredentialRepository.existsByUserId(principal.userId())
        );
    }

    /**
     * Also reports the scopes of the API token behind the request, so token clients (e.g. the MCP
     * server) can check what they hold. Web sessions get {@code null}.
     */
    public AuthMeResponse from(PlatformPrincipal principal, Authentication authentication) {
        List<String> tokenScopes = "api_token".equals(principal.oauthProvider()) && authentication != null
                ? authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(authority -> authority.startsWith("SCOPE_"))
                        .map(authority -> authority.substring("SCOPE_".length()))
                        .sorted()
                        .toList()
                : null;
        return AuthMeResponse.from(
                principal,
                localCredentialRepository.existsByUserId(principal.userId()),
                tokenScopes
        );
    }
}
