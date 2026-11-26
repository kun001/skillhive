package com.iflytek.skillhub.dto.knowledge;

import com.iflytek.skillhub.domain.knowledge.KnowledgeFolderShareVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;

public record UpdateKnowledgeShareRequest(
        @NotNull Boolean enabled,
        @NotNull List<@NotNull @Valid FolderSetting> folders,
        Expiration expiration,
        Boolean shareRootFiles
) {
    /** An omitted object preserves the setting; an object with no timestamp clears it. */
    public record Expiration(Instant expiresAt) {}

    public record FolderSetting(
            @NotNull @Positive Long folderId,
            @NotNull KnowledgeFolderShareVisibility visibility
    ) {}
}
