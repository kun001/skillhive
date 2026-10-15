package com.iflytek.skillhub.dto.knowledge;

import java.util.List;

/**
 * File detail with its knowledge base and the folder path from the root.
 *
 * @param textStatus state of the agent-readable Markdown text of the current version:
 *                   PENDING, RUNNING, COMPLETED, UNSUPPORTED or FAILED; {@code null} when none is queued
 * @param summary model-generated summary of the current version, when available
 */
public record KnowledgeDocumentDetailResponse(
        KnowledgeDocumentResponse document,
        KnowledgeBaseResponse knowledgeBase,
        List<KnowledgeFolderPathItem> folderPath,
        String sha256,
        String textStatus,
        String summary,
        List<String> keywords
) {
}
