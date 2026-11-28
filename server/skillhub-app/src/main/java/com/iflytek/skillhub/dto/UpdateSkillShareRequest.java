package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateSkillShareRequest(@NotNull Boolean enabled, VisitorShareExpiration expiration) {}
