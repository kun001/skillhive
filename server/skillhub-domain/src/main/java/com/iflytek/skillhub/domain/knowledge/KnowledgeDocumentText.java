package com.iflytek.skillhub.domain.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Agent-readable Markdown text of one file version, and the durable extraction job that
 * produces it. The original file stays the source of truth; this text is a derived rendition.
 */
@Entity
@Table(name = "knowledge_document_text")
public class KnowledgeDocumentText {
    public static final String PENDING = "PENDING";
    public static final String COMPLETED = "COMPLETED";
    public static final String UNSUPPORTED = "UNSUPPORTED";
    public static final String FAILED = "FAILED";

    @Id
    @Column(name = "version_id")
    private Long versionId;
    @Column(name = "document_id", nullable = false)
    private Long documentId;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "claim_token", length = 36)
    private String claimToken;
    @Column(length = 40)
    private String extractor;
    @Column(columnDefinition = "TEXT")
    private String content;
    @Column(name = "char_count")
    private Integer charCount;
    @Column(nullable = false)
    private boolean truncated;
    @Column(name = "search_tokens", columnDefinition = "TEXT")
    private String searchTokens;
    @Column(name = "extracted_at")
    private Instant extractedAt;
    @Column(name = "error_code", length = 40)
    private String errorCode;

    protected KnowledgeDocumentText() {
    }

    public KnowledgeDocumentText(Long versionId, Long documentId, Instant now) {
        this.versionId = versionId;
        this.documentId = documentId;
        this.status = PENDING;
        this.nextAttemptAt = now;
    }

    public boolean isCompleted() { return COMPLETED.equals(status); }
    public Long getVersionId() { return versionId; }
    public Long getDocumentId() { return documentId; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getExtractor() { return extractor; }
    public String getContent() { return content; }
    public Integer getCharCount() { return charCount; }
    public boolean isTruncated() { return truncated; }
    public Instant getExtractedAt() { return extractedAt; }
    public String getErrorCode() { return errorCode; }
}
