package com.iflytek.skillhub.domain.knowledge;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "knowledge_base")
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(nullable = false, length = 128)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeBaseStatus status = KnowledgeBaseStatus.ACTIVE;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeBase() {
    }

    public KnowledgeBase(Long namespaceId, String slug, String displayName, String description, String createdBy) {
        this.namespaceId = namespaceId;
        this.slug = slug;
        this.displayName = displayName;
        this.description = description;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
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

    public void rename(String displayName, String description, String updatedBy) {
        this.displayName = displayName;
        this.description = description;
        this.updatedBy = updatedBy;
    }

    public Long getId() { return id; }
    public Long getNamespaceId() { return namespaceId; }
    public String getSlug() { return slug; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public KnowledgeBaseStatus getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
