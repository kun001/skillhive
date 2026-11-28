package com.iflytek.skillhub.dto.knowledge;

import com.iflytek.skillhub.dto.VisitorShareExpiration;
import com.iflytek.skillhub.domain.knowledge.KnowledgeFolderShareVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record UpdateKnowledgeShareRequest(
        @NotNull Boolean enabled,
        @NotNull List<@NotNull @Valid FolderSetting> folders,
        VisitorShareExpiration expiration,
        Boolean shareRootFiles
) {
    public record FolderSetting(
            @NotNull @Positive Long folderId,
            @NotNull KnowledgeFolderShareVisibility visibility
    ) {}
}
