package com.iflytek.skillhub.domain.skill;

import com.iflytek.skillhub.domain.shared.VisitorSharePolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skill_share")
public class SkillShare {
    @Id
    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "share_id", nullable = false)
    private UUID shareId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected SkillShare() {}

    public SkillShare(Long skillId) {
        this.skillId = skillId;
        this.shareId = UUID.randomUUID();
    }

    public Long getSkillId() { return skillId; }
    public UUID getShareId() { return shareId; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isActiveAt(Instant now) {
        return VisitorSharePolicy.isActive(shareId, expiresAt, now);
    }

    public void updateExpiration(Instant expiration, Instant now) {
        shareId = VisitorSharePolicy.enable(shareId, expiresAt, expiration, now);
        expiresAt = expiration;
    }
}
