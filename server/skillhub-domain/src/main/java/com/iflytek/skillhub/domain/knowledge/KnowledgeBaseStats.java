package com.iflytek.skillhub.domain.knowledge;

import java.time.Instant;

/** Active file count and most recent file change for one knowledge base. */
public record KnowledgeBaseStats(Long knowledgeBaseId, long documentCount, Instant lastUpdatedAt) {
}
