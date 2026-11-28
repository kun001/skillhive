package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillShareVisibility;
import jakarta.validation.constraints.NotNull;

public record UpdateSkillShareRequest(@NotNull Boolean enabled, VisitorShareExpiration expiration, SkillShareVisibility visibility) {}
