package com.iflytek.skillhub.domain.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for knowledge bases.
 */
public interface KnowledgeBaseRepository {
    Optional<KnowledgeBase> findById(Long id);
    Optional<KnowledgeBase> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    List<KnowledgeBase> findByNamespaceIdInAndStatus(Collection<Long> namespaceIds, KnowledgeBaseStatus status);
    List<KnowledgeBase> findByStatus(KnowledgeBaseStatus status);
    boolean existsByNamespaceIdAndSlug(Long namespaceId, String slug);
    KnowledgeBase save(KnowledgeBase knowledgeBase);
}
