package com.iflytek.skillhub.dto.knowledge;

/**
 * A file found by a cross knowledge base search, with the knowledge base and namespace it belongs to.
 */
public record KnowledgeDocumentSearchHitResponse(
        KnowledgeDocumentResponse document,
        String namespace,
        String namespaceDisplayName,
        String knowledgeBaseSlug,
        String knowledgeBaseDisplayName
) {
}
