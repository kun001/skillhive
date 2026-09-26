package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersion;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentVersionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed repository for knowledge document versions.
 */
@Repository
public interface KnowledgeDocumentVersionJpaRepository
        extends JpaRepository<KnowledgeDocumentVersion, Long>, KnowledgeDocumentVersionRepository {
    List<KnowledgeDocumentVersion> findByIdIn(Collection<Long> ids);
    List<KnowledgeDocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);
    Optional<KnowledgeDocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, int versionNumber);
}
