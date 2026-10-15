package com.iflytek.skillhub.dto.knowledge;

/**
 * A file found by a cross knowledge base search, with the knowledge base and namespace it belongs to.
 *
 * @param summary model-generated summary of the current version, when available
 * @param snippet excerpt of the extracted text around the first query term, when it occurs there
 */
public record KnowledgeDocumentSearchHitResponse(
        KnowledgeDocumentResponse document,
        String namespace,
        String namespaceDisplayName,
        String knowledgeBaseSlug,
        String knowledgeBaseDisplayName,
        String summary,
        String snippet
) {
}
