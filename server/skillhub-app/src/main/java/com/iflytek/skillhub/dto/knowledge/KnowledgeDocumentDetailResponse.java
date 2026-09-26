package com.iflytek.skillhub.dto.knowledge;

import java.util.List;

/**
 * File detail with its knowledge base and the folder path from the root.
 */
public record KnowledgeDocumentDetailResponse(
        KnowledgeDocumentResponse document,
        KnowledgeBaseResponse knowledgeBase,
        List<KnowledgeFolderPathItem> folderPath,
        String sha256
) {
}
