package com.iflytek.skillhub.domain.skill;

import com.iflytek.skillhub.domain.shared.VisitorSharePolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    // Entry-only tokens stay out of the lookup used by versions before entry-only sharing.
    @Column(name = "entry_share_id")
    private UUID entryShareId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    private SkillShareVisibility visibility = SkillShareVisibility.PUBLIC;

    protected SkillShare() {}

    public SkillShare(Long skillId) {
        this.skillId = skillId;
        this.shareId = UUID.randomUUID();
    }

    public Long getSkillId() { return skillId; }
    public UUID getShareId() { return visibility == SkillShareVisibility.ENTRY ? entryShareId : shareId; }
    public Instant getExpiresAt() { return expiresAt; }
    public SkillShareVisibility getVisibility() { return visibility; }

    public void setVisibility(SkillShareVisibility visibility) {
        if (this.visibility == visibility) return;
        if (visibility == SkillShareVisibility.ENTRY) {
            entryShareId = shareId;
            shareId = UUID.randomUUID();
        } else {
            shareId = entryShareId;
            entryShareId = null;
        }
        this.visibility = visibility;
    }

    public boolean isActiveAt(Instant now) {
        return VisitorSharePolicy.isActive(getShareId(), expiresAt, now);
    }

    public void updateExpiration(Instant expiration, Instant now) {
        UUID activeId = VisitorSharePolicy.enable(getShareId(), expiresAt, expiration, now);
        if (visibility == SkillShareVisibility.ENTRY) {
            entryShareId = activeId;
        } else {
            shareId = activeId;
        }
        expiresAt = expiration;
    }
}
