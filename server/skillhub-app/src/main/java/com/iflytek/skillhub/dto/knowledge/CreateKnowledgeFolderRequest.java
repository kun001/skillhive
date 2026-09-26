package com.iflytek.skillhub.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKnowledgeFolderRequest(
        @NotBlank @Size(max = 128) String name,
        Long parentId
) {
}
