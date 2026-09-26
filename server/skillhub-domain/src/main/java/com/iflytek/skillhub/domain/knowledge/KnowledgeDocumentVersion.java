package com.iflytek.skillhub.domain.knowledge;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "knowledge_document_version")
public class KnowledgeDocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeDocumentVersionStatus status = KnowledgeDocumentVersionStatus.DRAFT;

    @Column(name = "content_object_key", nullable = false, length = 512)
    private String contentObjectKey;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "source_filename", length = 256)
    private String sourceFilename;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "change_note", length = 512)
    private String changeNote;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_by", length = 128)
    private String publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    protected KnowledgeDocumentVersion() {
    }

    public KnowledgeDocumentVersion(Long documentId,
                                    int versionNumber,
                                    String contentObjectKey,
                                    String contentType,
                                    String sourceFilename,
                                    long sizeBytes,
                                    String sha256,
                                    String changeNote,
                                    String createdBy) {
        this.documentId = documentId;
        this.versionNumber = versionNumber;
        this.contentObjectKey = contentObjectKey;
        this.contentType = contentType;
        this.sourceFilename = sourceFilename;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.changeNote = changeNote;
        this.createdBy = createdBy;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    /** Knowledge files are published directly on upload; review is not enabled for this domain yet. */
    public void publishDirectly(String publisherId) {
        this.status = KnowledgeDocumentVersionStatus.PUBLISHED;
        this.publishedBy = publisherId;
        this.publishedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public int getVersionNumber() { return versionNumber; }
    public KnowledgeDocumentVersionStatus getStatus() { return status; }
    public String getContentObjectKey() { return contentObjectKey; }
    public String getContentType() { return contentType; }
    public String getSourceFilename() { return sourceFilename; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getChangeNote() { return changeNote; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPublishedBy() { return publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getRejectReason() { return rejectReason; }
}
