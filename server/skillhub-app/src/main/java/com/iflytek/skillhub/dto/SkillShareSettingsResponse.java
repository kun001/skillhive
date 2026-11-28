package com.iflytek.skillhub.dto;

import java.time.Instant;

public record SkillShareSettingsResponse(String shareId, Instant expiresAt) {}
