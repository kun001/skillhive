package com.iflytek.skillhub.domain.knowledge;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for knowledge folders.
 */
public interface KnowledgeFolderRepository {
    Optional<KnowledgeFolder> findById(Long id);
    List<KnowledgeFolder> findByKnowledgeBaseId(Long knowledgeBaseId);
    boolean existsByParentId(Long parentId);
    KnowledgeFolder save(KnowledgeFolder folder);
    void delete(KnowledgeFolder folder);
}
