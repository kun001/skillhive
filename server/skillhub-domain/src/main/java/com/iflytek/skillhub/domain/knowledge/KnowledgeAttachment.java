package com.iflytek.skillhub.domain.knowledge;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

/** A raster image belonging to one published Markdown version, never a separate document. */
@Entity
@Table(name = "knowledge_attachment")
public class KnowledgeAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "document_version_id", nullable = false)
    private Long documentVersionId;
    @Column(name = "relative_path", nullable = false, length = 1024)
    private String relativePath;
    @Column(name = "file_name", nullable = false, length = 256)
    private String fileName;
    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected KnowledgeAttachment() {}

    public KnowledgeAttachment(Long versionId, String path, String fileName, String contentType,
                               long sizeBytes, String sha256, String objectKey) {
        this.documentVersionId = versionId;
        this.relativePath = path;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.objectKey = objectKey;
    }

    @PrePersist
    void prePersist() { createdAt = Instant.now(Clock.systemUTC()); }

    public KnowledgeAttachment copyTo(Long versionId) {
        return new KnowledgeAttachment(versionId, relativePath, fileName, contentType, sizeBytes, sha256, objectKey);
    }

    public Long getId() { return id; }
    public Long getDocumentVersionId() { return documentVersionId; }
    public String getRelativePath() { return relativePath; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getObjectKey() { return objectKey; }
}
