package com.iflytek.skillhub.dto.knowledge;

import java.time.Instant;
import java.util.List;

/**
 * Agent-readable Markdown of one file version, derived from the original file.
 *
 * @param status PENDING, RUNNING, COMPLETED, UNSUPPORTED or FAILED; content is present only when COMPLETED
 * @param truncated whether text beyond the extraction limit was dropped
 */
public record KnowledgeDocumentTextResponse(
        Long documentId,
        int versionNumber,
        String title,
        String fileName,
        String status,
        String extractor,
        Integer charCount,
        boolean truncated,
        Instant extractedAt,
        String summary,
        List<String> keywords,
        String content
) {
}
