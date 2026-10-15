package com.iflytek.skillhub.domain.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** Model-generated summary and keywords of one file version, and the durable job that produces them. */
@Entity
@Table(name = "knowledge_document_summary")
public class KnowledgeDocumentSummary {
    public static final String COMPLETED = "COMPLETED";

    @Id
    @Column(name = "version_id")
    private Long versionId;
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
    @Column(columnDefinition = "TEXT")
    private String summary;
    @Column(columnDefinition = "TEXT")
    private String keywords;
    @Column(name = "search_tokens", columnDefinition = "TEXT")
    private String searchTokens;
    @Column(name = "generated_at")
    private Instant generatedAt;
    @Column(name = "error_code", length = 40)
    private String errorCode;

    protected KnowledgeDocumentSummary() {
    }

    public KnowledgeDocumentSummary(Long versionId, Instant now) {
        this.versionId = versionId;
        this.status = "PENDING";
        this.nextAttemptAt = now;
    }

    public boolean isCompleted() { return COMPLETED.equals(status); }
    public Long getVersionId() { return versionId; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getSummary() { return summary; }
    public List<String> getKeywords() {
        return keywords == null || keywords.isBlank() ? List.of() : Arrays.stream(keywords.split("\n")).filter(k -> !k.isBlank()).toList();
    }
    public Instant getGeneratedAt() { return generatedAt; }
}
