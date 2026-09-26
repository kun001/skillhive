package com.iflytek.skillhub.domain.knowledge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Domain repository contract for knowledge documents.
 */
public interface KnowledgeDocumentRepository {
    Optional<KnowledgeDocument> findById(Long id);
    boolean existsByKnowledgeBaseIdAndSlug(Long knowledgeBaseId, String slug);
    boolean existsActiveInFolder(Long folderId);
    Page<KnowledgeDocument> search(KnowledgeDocumentSearch search, Pageable pageable);
    List<KnowledgeBaseStats> statsByKnowledgeBaseIds(Collection<Long> knowledgeBaseIds);
    /** Active document counts keyed by folder id; documents outside any folder are omitted. */
    Map<Long, Long> countActiveByFolder(Long knowledgeBaseId);
    KnowledgeDocument save(KnowledgeDocument document);
}
