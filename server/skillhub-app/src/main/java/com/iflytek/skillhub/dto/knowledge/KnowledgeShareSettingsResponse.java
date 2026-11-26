package com.iflytek.skillhub.dto.knowledge;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeShareSettingsResponse(String shareId, Instant expiresAt, Boolean shareRootFiles) {
    public static KnowledgeShareSettingsResponse forBase(UUID shareId, Instant expiresAt, boolean shareRootFiles) {
        return new KnowledgeShareSettingsResponse(shareId == null ? null : shareId.toString(), expiresAt, shareRootFiles);
    }
}
