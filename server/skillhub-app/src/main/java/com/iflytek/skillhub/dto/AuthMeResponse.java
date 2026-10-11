package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;

import java.util.List;
import java.util.Set;

public record AuthMeResponse(
        String userId,
        String displayName,
        String email,
        String avatarUrl,
        String oauthProvider,
        boolean canChangePassword,
        Set<String> platformRoles,
        // Scopes of the API token that authenticated the request; null for web sessions.
        List<String> tokenScopes
) {
    public static AuthMeResponse from(PlatformPrincipal principal, boolean canChangePassword) {
        return from(principal, canChangePassword, null);
    }

    public static AuthMeResponse from(PlatformPrincipal principal, boolean canChangePassword, List<String> tokenScopes) {
        return new AuthMeResponse(
                principal.userId(),
                principal.displayName(),
                principal.email() != null ? principal.email() : "",
                principal.avatarUrl() != null ? principal.avatarUrl() : "",
                principal.oauthProvider(),
                canChangePassword,
                principal.platformRoles(),
                tokenScopes
        );
    }
}
