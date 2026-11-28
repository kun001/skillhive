package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillShareVisibility;
import java.time.Instant;

public record SkillShareSettingsResponse(String shareId, Instant expiresAt, SkillShareVisibility visibility) {}
