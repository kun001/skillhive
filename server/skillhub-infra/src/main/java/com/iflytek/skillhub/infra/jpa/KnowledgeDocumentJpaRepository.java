package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.knowledge.KnowledgeDocument;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for knowledge documents; exposed to the domain through
 * {@link JpaKnowledgeDocumentRepositoryAdapter}.
 */
@Repository
public interface KnowledgeDocumentJpaRepository
        extends JpaRepository<KnowledgeDocument, Long>, JpaSpecificationExecutor<KnowledgeDocument> {

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from KnowledgeDocument d where d.id = :id")
    Optional<KnowledgeDocument> findByIdForUpdate(@Param("id") Long id);

    boolean existsByKnowledgeBaseIdAndSlug(Long knowledgeBaseId, String slug);
    @Query(value = "select exists(select 1 from knowledge_document d join knowledge_document_version v on v.id = d.published_version_id "
            + "where d.knowledge_base_id = :baseId and d.status = 'ACTIVE' and d.folder_id is not distinct from :folderId "
            + "and lower(v.source_filename) = lower(:filename))", nativeQuery = true)
    boolean existsActiveFilename(@Param("baseId") Long baseId, @Param("folderId") Long folderId, @Param("filename") String filename);

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
