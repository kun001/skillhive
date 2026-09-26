package com.iflytek.skillhub.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param slug optional; generated from the display name when omitted
 */
public record CreateKnowledgeBaseRequest(
        @NotBlank @Size(max = 64) String namespace,
        @Size(max = 64) String slug,
        @NotBlank @Size(max = 128) String displayName,
        @Size(max = 2000) String description
) {
}
