package com.iflytek.skillhub.domain.knowledge;

import java.util.List;
import java.util.Optional;

public interface KnowledgeAttachmentRepository {
    KnowledgeAttachment save(KnowledgeAttachment attachment);
    List<KnowledgeAttachment> findByDocumentVersionId(Long versionId);
    Optional<KnowledgeAttachment> findById(Long id);
}
