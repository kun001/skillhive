package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.token.ApiTokenAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Transport-level audit fields extracted from the current HTTP request.
 *
 * @param apiTokenId the API token that authenticated the request, or {@code null} for a web session
 */
public record AuditRequestContext(
        String clientIp,
        String userAgent,
        Long apiTokenId
) {
    public AuditRequestContext(String clientIp, String userAgent) {
        this(clientIp, userAgent, null);
    }

    public static AuditRequestContext from(HttpServletRequest request) {
        if (request == null) {
            return new AuditRequestContext(null, null);
        }
        return new AuditRequestContext(
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getAttribute(ApiTokenAuthenticationFilter.API_TOKEN_ID_ATTRIBUTE) instanceof Long id ? id : null
        );
    }
}
