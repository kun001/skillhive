package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.knowledge.KnowledgeDocument;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data repository for knowledge documents; exposed to the domain through
 * {@link JpaKnowledgeDocumentRepositoryAdapter}.
 */
@Repository
public interface KnowledgeDocumentJpaRepository
        extends JpaRepository<KnowledgeDocument, Long>, JpaSpecificationExecutor<KnowledgeDocument> {

    boolean existsByKnowledgeBaseIdAndSlug(Long knowledgeBaseId, String slug);

    boolean existsByFolderIdAndStatus(Long folderId, KnowledgeDocumentStatus status);

    @Query("""
            select d.knowledgeBaseId, count(d), max(d.updatedAt)
            from KnowledgeDocument d
            where d.knowledgeBaseId in :baseIds and d.status = :status
            group by d.knowledgeBaseId
            """)
    List<Object[]> statsByKnowledgeBaseIds(@Param("baseIds") Collection<Long> baseIds,
                                           @Param("status") KnowledgeDocumentStatus status);

    @Query("""
            select d.folderId, count(d)
            from KnowledgeDocument d
            where d.knowledgeBaseId = :baseId and d.status = :status and d.folderId is not null
            group by d.folderId
            """)
    List<Object[]> countByFolder(@Param("baseId") Long baseId, @Param("status") KnowledgeDocumentStatus status);
}
