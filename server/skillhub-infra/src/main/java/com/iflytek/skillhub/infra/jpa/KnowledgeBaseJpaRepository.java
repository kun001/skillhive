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
    @org.springframework.data.jpa.repository.Query(value = "select * from knowledge_base where id = :id for update", nativeQuery = true)
    Optional<KnowledgeBase> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<KnowledgeBase> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    @org.springframework.data.jpa.repository.Query(value = "select * from knowledge_base where namespace_id = :namespaceId and slug = :slug for update", nativeQuery = true)
    Optional<KnowledgeBase> findByNamespaceIdAndSlugForUpdate(@org.springframework.data.repository.query.Param("namespaceId") Long namespaceId,
            @org.springframework.data.repository.query.Param("slug") String slug);
    List<KnowledgeBase> findByNamespaceIdInAndStatus(Collection<Long> namespaceIds, KnowledgeBaseStatus status);
    List<KnowledgeBase> findByStatus(KnowledgeBaseStatus status);
    boolean existsByNamespaceIdAndSlug(Long namespaceId, String slug);
}
