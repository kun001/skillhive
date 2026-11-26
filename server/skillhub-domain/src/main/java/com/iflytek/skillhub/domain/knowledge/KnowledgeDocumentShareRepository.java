package com.iflytek.skillhub.domain.knowledge;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgeDocumentShareRepository {
    Optional<KnowledgeDocumentShare> findById(Long documentId);
    Optional<KnowledgeDocumentShare> findByShareId(UUID shareId);
    <S extends KnowledgeDocumentShare> S save(S share);
    void delete(KnowledgeDocumentShare share);
}
