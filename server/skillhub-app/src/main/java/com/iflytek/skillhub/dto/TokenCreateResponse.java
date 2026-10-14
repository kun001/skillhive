package com.iflytek.skillhub.dto;

import java.util.List;

public record TokenCreateResponse(
        String token,
        Long id,
        String name,
        String tokenPrefix,
        String createdAt,
        String expiresAt,
        List<String> scopes,
        com.iflytek.skillhub.auth.token.McpTokenGrants resourceGrants
) {
    public TokenCreateResponse(String token, Long id, String name, String prefix, String createdAt, String expiresAt,
            List<String> scopes) { this(token, id, name, prefix, createdAt, expiresAt, scopes, null); }
}
