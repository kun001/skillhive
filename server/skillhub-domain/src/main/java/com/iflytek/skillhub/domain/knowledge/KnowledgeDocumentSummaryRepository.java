package com.iflytek.skillhub.domain.knowledge;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

/** Summaries and their generation queue; claims are atomic leases shared by all instances. */
public interface KnowledgeDocumentSummaryRepository {
    Optional<KnowledgeDocumentSummary> findById(Long versionId);
    List<KnowledgeDocumentSummary> findByVersionIdIn(Collection<Long> versionIds);
    KnowledgeDocumentSummary save(KnowledgeDocumentSummary summary);
    List<KnowledgeDocumentSummary> findReady(Instant now, int maxAttempts, Pageable page);
    int claim(Long versionId, String token, Instant now, Instant lease, int maxAttempts);
    int complete(Long versionId, String token, String summary, String keywords, String searchTokens, Instant now);
    int fail(Long versionId, String token, String status, Instant next, String error);
    int expireExhausted(Instant now, int maxAttempts);
    /**
     * Queues summaries for current versions whose text exists but that were never queued, for
     * example files extracted before summaries were enabled. Returns the number queued.
     */
    int enqueueMissing(Instant now, int limit);
}
