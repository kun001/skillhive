package com.iflytek.skillhub.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Full replacement of a file's title, description and folder; a {@code null} folder means the root.
 */
public record UpdateKnowledgeDocumentRequest(
        @NotBlank @Size(max = 256) String title,
        @Size(max = 2000) String description,
        Long folderId
) {
}
