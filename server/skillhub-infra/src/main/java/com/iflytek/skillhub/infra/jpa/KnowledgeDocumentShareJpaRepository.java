package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentShare;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentShareRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentShareJpaRepository
        extends JpaRepository<KnowledgeDocumentShare, Long>, KnowledgeDocumentShareRepository {
}
