package com.iflytek.skillhub.dto.knowledge;

import com.iflytek.skillhub.domain.knowledge.KnowledgePreviewKind;

public record SharedKnowledgeFileResponse(
        Long id,
        Long folderId,
        String title,
        String fileName,
        String fileExtension,
        long sizeBytes,
        int currentVersion,
        KnowledgePreviewKind previewKind,
        boolean canDownload
) {}
