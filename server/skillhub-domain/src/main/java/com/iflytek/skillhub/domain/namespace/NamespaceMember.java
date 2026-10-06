package com.iflytek.skillhub.domain.namespace;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "namespace_member",
       uniqueConstraints = @UniqueConstraint(columnNames = {"namespace_id", "user_id"}))
public class NamespaceMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NamespaceRole role;

    @Column(name = "can_edit", nullable = false)
    private boolean canEdit = true;

    @Column(name = "can_download", nullable = false)
    private boolean canDownload = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NamespaceMember() {}

    public NamespaceMember(Long namespaceId, String userId, NamespaceRole role) {
        this.namespaceId = namespaceId;
        this.userId = userId;
        this.role = role;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now(Clock.systemUTC());
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Long getNamespaceId() { return namespaceId; }
    public void setNamespaceId(Long namespaceId) { this.namespaceId = namespaceId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public NamespaceRole getRole() { return role; }
    public void setRole(NamespaceRole role) { this.role = role; }
    public boolean canEdit() { return role != NamespaceRole.MEMBER || canEdit; }
    public boolean canDownload() { return role != NamespaceRole.MEMBER || canDownload; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }
    public void setCanDownload(boolean canDownload) { this.canDownload = canDownload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
