package com.iflytek.skillhub.dto;

import java.util.List;

public record TokenSummaryResponse(
        Long id,
        String name,
        String tokenPrefix,
        String createdAt,
        String expiresAt,
        String lastUsedAt,
        List<String> scopes,
        com.iflytek.skillhub.auth.token.McpTokenGrants resourceGrants
) {
    public TokenSummaryResponse(Long id, String name, String prefix, String createdAt, String expiresAt,
            String lastUsedAt, List<String> scopes) { this(id, name, prefix, createdAt, expiresAt, lastUsedAt, scopes, null); }
}
