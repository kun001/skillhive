package com.iflytek.skillhub.dto;

import java.time.Instant;

/**
 * The Markdown guide of a team space.
 *
 * @param content the guide, empty when the team has not written one
 * @param canEdit whether the caller may change it (team owner or admin)
 */
public record NamespaceGuideResponse(
        String namespace,
        String namespaceDisplayName,
        String content,
        String updatedBy,
        Instant updatedAt,
        boolean canEdit
) {
}
