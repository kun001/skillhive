package com.iflytek.skillhub.domain.knowledge;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "knowledge_folder")
public class KnowledgeFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeFolder() {
    }

    public KnowledgeFolder(Long knowledgeBaseId, Long parentId, String name, String createdBy) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.parentId = parentId;
        this.name = name;
        this.createdBy = createdBy;
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

    public void rename(String name) {
        this.name = name;
    }

    public void moveTo(Long parentId) {
        this.parentId = parentId;
    }

    public Long getId() { return id; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public Long getParentId() { return parentId; }
    public String getName() { return name; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
