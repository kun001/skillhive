package com.iflytek.skillhub.dto.knowledge;

import jakarta.validation.constraints.NotNull;

public record UpdateKnowledgeDocumentShareRequest(@NotNull Boolean enabled, UpdateKnowledgeShareRequest.Expiration expiration) {}
