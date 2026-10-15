package com.iflytek.skillhub.domain.namespace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The Markdown guide of a team space: conventions, where things are and how to use them. */
@Entity
@Table(name = "namespace_guide")
public class NamespaceGuide {
    public static final int MAX_LENGTH = 20_000;

    @Id
    @Column(name = "namespace_id")
    private Long namespaceId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NamespaceGuide() {
    }

    public NamespaceGuide(Long namespaceId, String content, String updatedBy, Instant updatedAt) {
        this.namespaceId = namespaceId;
        update(content, updatedBy, updatedAt);
    }

    public void update(String content, String updatedBy, Instant updatedAt) {
        this.content = content;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public Long getNamespaceId() { return namespaceId; }
    public String getContent() { return content; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
