package com.iflytek.skillhub.domain.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_document_share")
public class KnowledgeDocumentShare {
    public static final String URL_PREFIX = "file-";

    @Id
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "share_id", nullable = false)
    private UUID shareId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected KnowledgeDocumentShare() {}

    public KnowledgeDocumentShare(Long documentId) {
        this.documentId = documentId;
        this.shareId = UUID.randomUUID();
    }

    public Long getDocumentId() { return documentId; }
    public UUID getShareId() { return shareId; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isActiveAt(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    public void updateExpiration(Instant expiration, Instant now) {
        if (!isActiveAt(now) && (expiration == null || expiration.isAfter(now))) {
            shareId = UUID.randomUUID();
        }
        expiresAt = expiration;
    }
}
