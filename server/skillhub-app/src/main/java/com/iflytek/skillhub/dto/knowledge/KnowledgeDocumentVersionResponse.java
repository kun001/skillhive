package com.iflytek.skillhub.dto.knowledge;

import java.time.Instant;

public record KnowledgeDocumentVersionResponse(
        Long id,
        int versionNumber,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256,
        String changeNote,
        KnowledgeUserResponse createdBy,
        Instant createdAt,
        boolean current
) {
}
