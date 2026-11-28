package com.iflytek.skillhub.dto;

import java.time.Instant;

public record SharedSkillResponse(String displayName, String summary, String version, Instant expiresAt) {}
