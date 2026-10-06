package com.iflytek.skillhub.domain.knowledge;

import java.time.Instant;
import java.util.Collection;

/**
 * Metadata-only filters for listing files in one or more knowledge bases. File contents are never searched.
 *
 * @param knowledgeBaseIds the knowledge bases to search; an empty collection matches nothing
 * @param folderIds restricts results to these folders; {@code null} means every folder
 * @param keyword matched case-insensitively against title and description
 * @param extensions lower-case file extensions; {@code null} or empty means every type
 */
public record KnowledgeDocumentSearch(
        Collection<Long> knowledgeBaseIds,
        Collection<Long> folderIds,
        String keyword,
        Collection<String> extensions,
        String ownerId,
        Instant updatedFrom,
        Instant updatedTo
) {
}
