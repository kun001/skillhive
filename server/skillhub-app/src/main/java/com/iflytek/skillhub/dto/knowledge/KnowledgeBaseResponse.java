package com.iflytek.skillhub.dto.knowledge;

import java.time.Instant;

/**
 * A knowledge base with its namespace, file statistics and the caller's permissions.
 */
public record KnowledgeBaseResponse(
        Long id,
        String namespace,
        String namespaceDisplayName,
        String slug,
        String displayName,
        String description,
        String status,
        long documentCount,
        Instant updatedAt,
        boolean canManage,
        boolean canContribute
) {
}
