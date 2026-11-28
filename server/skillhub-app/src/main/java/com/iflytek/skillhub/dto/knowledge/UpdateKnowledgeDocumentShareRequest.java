package com.iflytek.skillhub.dto.knowledge;

import com.iflytek.skillhub.dto.VisitorShareExpiration;
import jakarta.validation.constraints.NotNull;

public record UpdateKnowledgeDocumentShareRequest(@NotNull Boolean enabled, VisitorShareExpiration expiration) {}
