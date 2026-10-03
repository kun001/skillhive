package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.knowledge.KnowledgeAttachment;
import com.iflytek.skillhub.domain.knowledge.KnowledgeAttachmentRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KnowledgeAttachmentJpaRepository
        extends JpaRepository<KnowledgeAttachment, Long>, KnowledgeAttachmentRepository {}
