package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.knowledge.KnowledgeFolder;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFolderRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA-backed repository for knowledge folders.
 */
@Repository
public interface KnowledgeFolderJpaRepository extends JpaRepository<KnowledgeFolder, Long>, KnowledgeFolderRepository {
    List<KnowledgeFolder> findByKnowledgeBaseId(Long knowledgeBaseId);
    boolean existsByParentId(Long parentId);
}
