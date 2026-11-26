package com.iflytek.skillhub.dto.knowledge;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchDeleteKnowledgeDocumentsRequest(
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Positive Long> documentIds
) {}
