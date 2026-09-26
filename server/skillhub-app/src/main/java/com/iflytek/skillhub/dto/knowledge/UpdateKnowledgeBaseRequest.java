package com.iflytek.skillhub.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateKnowledgeBaseRequest(
        @NotBlank @Size(max = 128) String displayName,
        @Size(max = 2000) String description
) {
}
