package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Durable, version-scoped generation job. Its status is separate from skill lifecycle state. */
@Entity
@Table(name = "skill_introduction")
public class SkillIntroduction {
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
    @Column(name = "zh_function_description", columnDefinition = "TEXT")
    private String zhFunctionDescription;
    @Column(name = "zh_usage_instructions", columnDefinition = "TEXT")
    private String zhUsageInstructions;
    @Column(name = "en_function_description", columnDefinition = "TEXT")
    private String enFunctionDescription;
    @Column(name = "en_usage_instructions", columnDefinition = "TEXT")
    private String enUsageInstructions;
    @Column(name = "generated_at")
    private Instant generatedAt;
    @Column(name = "error_code", length = 40)
    private String errorCode;

    protected SkillIntroduction() {}

    public SkillIntroduction(Long versionId, Instant now) {
        this.versionId = versionId;
        this.status = "PENDING";
        this.nextAttemptAt = now;
    }

    public Long getVersionId() { return versionId; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getZhFunctionDescription() { return zhFunctionDescription; }
    public String getZhUsageInstructions() { return zhUsageInstructions; }
    public String getEnFunctionDescription() { return enFunctionDescription; }
    public String getEnUsageInstructions() { return enUsageInstructions; }
    public Instant getGeneratedAt() { return generatedAt; }
}
