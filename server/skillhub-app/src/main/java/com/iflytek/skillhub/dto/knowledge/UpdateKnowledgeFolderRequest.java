package com.iflytek.skillhub.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Full replacement of a folder's name and location; a {@code null} parent moves it to the root.
 */
public record UpdateKnowledgeFolderRequest(
        @NotBlank @Size(max = 128) String name,
        Long parentId
) {
}
