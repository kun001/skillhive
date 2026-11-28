package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillShareVisibility;
import java.time.Instant;

public record SharedSkillResponse(String displayName, String summary, String version, Instant expiresAt,
                                  SkillShareVisibility visibility, SkillWorkspace memberWorkspace) {
    public record SkillWorkspace(String namespace, String slug) {}
}
