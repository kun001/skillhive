package com.iflytek.skillhub.domain.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for knowledge document versions.
 */
public interface KnowledgeDocumentVersionRepository {
    Optional<KnowledgeDocumentVersion> findById(Long id);
    List<KnowledgeDocumentVersion> findByIdIn(Collection<Long> ids);
    List<KnowledgeDocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);
    Optional<KnowledgeDocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, int versionNumber);
    KnowledgeDocumentVersion save(KnowledgeDocumentVersion version);
}
