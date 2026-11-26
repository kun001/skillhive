package com.iflytek.skillhub.dto.knowledge;

import com.iflytek.skillhub.domain.knowledge.KnowledgeFolderShareVisibility;

/**
 * One folder in a flat list; clients assemble the tree from {@code parentId}.
 *
 * @param documentCount active files placed directly in this folder
 */
public record KnowledgeFolderResponse(
        Long id,
        Long parentId,
        String name,
        long documentCount,
        boolean canManage,
        KnowledgeFolderShareVisibility shareVisibility
) {
}
