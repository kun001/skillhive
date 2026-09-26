package com.iflytek.skillhub.domain.knowledge;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(nullable = false, length = 128)
    private String slug;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner_id", nullable = false, length = 128)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeDocumentStatus status = KnowledgeDocumentStatus.ACTIVE;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "published_version_id")
    private Long publishedVersionId;

    @Column(name = "file_extension", length = 32)
    private String fileExtension;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(Long knowledgeBaseId,
                             Long folderId,
                             String slug,
                             String title,
                             String description,
                             String ownerId) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.folderId = folderId;
        this.slug = slug;
        this.title = title;
        this.description = description;
        this.ownerId = ownerId;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now(Clock.systemUTC());
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    /** Points the document at a published version and mirrors its extension for type filters. */
    public void publish(KnowledgeDocumentVersion version, String fileExtension) {
        if (version.getStatus() != KnowledgeDocumentVersionStatus.PUBLISHED) {
            throw new IllegalStateException("Only published versions can become current");
        }
        this.publishedVersionId = version.getId();
        this.fileExtension = fileExtension;
        this.updatedAt = Instant.now(Clock.systemUTC());
    }

    public void updateDetails(String title, String description, Long folderId) {
        this.title = title;
        this.description = description;
        this.folderId = folderId;
    }

    public void archive() {
        this.status = KnowledgeDocumentStatus.ARCHIVED;
    }

    public boolean isActive() {
        return status == KnowledgeDocumentStatus.ACTIVE;
    }

    public Long getId() { return id; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public Long getFolderId() { return folderId; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getOwnerId() { return ownerId; }
    public KnowledgeDocumentStatus getStatus() { return status; }
    public boolean isHidden() { return hidden; }
    public Long getPublishedVersionId() { return publishedVersionId; }
    public String getFileExtension() { return fileExtension; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
