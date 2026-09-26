package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.knowledge.KnowledgeBase;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseRepository;
import com.iflytek.skillhub.domain.knowledge.KnowledgeBaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed repository for knowledge bases.
 */
@Repository
public interface KnowledgeBaseJpaRepository extends JpaRepository<KnowledgeBase, Long>, KnowledgeBaseRepository {
    Optional<KnowledgeBase> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    List<KnowledgeBase> findByNamespaceIdInAndStatus(Collection<Long> namespaceIds, KnowledgeBaseStatus status);
    List<KnowledgeBase> findByStatus(KnowledgeBaseStatus status);
    boolean existsByNamespaceIdAndSlug(Long namespaceId, String slug);
}
