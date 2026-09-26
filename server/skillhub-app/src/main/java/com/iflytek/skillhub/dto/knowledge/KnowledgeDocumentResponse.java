package com.iflytek.skillhub.dto.knowledge;

import java.time.Instant;

/**
 * A knowledge file and its current version.
 *
 * @param previewKind PDF, IMAGE, MARKDOWN, TEXT or NONE (download only)
 */
public record KnowledgeDocumentResponse(
        Long id,
        Long knowledgeBaseId,
        Long folderId,
        String title,
        String description,
        String fileName,
        String fileExtension,
        String contentType,
        long sizeBytes,
        int currentVersion,
        String previewKind,
        KnowledgeUserResponse owner,
        Instant createdAt,
        Instant updatedAt,
        boolean canManage
) {
}
