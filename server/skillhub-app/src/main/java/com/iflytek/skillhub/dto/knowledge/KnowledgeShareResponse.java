package com.iflytek.skillhub.dto.knowledge;

import java.time.Instant;
import java.util.List;

public record KnowledgeShareResponse(
        String displayName,
        List<SharedKnowledgeFolder> folders,
        MemberWorkspace memberWorkspace,
        Instant expiresAt,
        Long documentId
) {
    public record SharedKnowledgeFolder(Long id, Long parentId, String name, boolean entryOnly) {}
    public record MemberWorkspace(String namespace, String base) {}
}
