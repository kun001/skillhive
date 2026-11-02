package com.iflytek.skillhub.dto.knowledge;

import java.time.Instant;

/** An archived (deleted) knowledge file that can still be permanently purged. */
public record ArchivedKnowledgeDocumentResponse(
        Long id,
        Long knowledgeBaseId,
        Long folderId,
        String title,
        KnowledgeUserResponse owner,
        Instant updatedAt,
        int versionCount,
        long storedBytes,
        boolean canPurge
) {
}
