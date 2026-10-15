package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentSummary;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentSummaryRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Atomic leases allow multiple application instances to share the durable summary queue. */
public interface KnowledgeDocumentSummaryJpaRepository
        extends JpaRepository<KnowledgeDocumentSummary, Long>, KnowledgeDocumentSummaryRepository {

    List<KnowledgeDocumentSummary> findByVersionIdIn(Collection<Long> versionIds);

    @Query("""
            SELECT s FROM KnowledgeDocumentSummary s WHERE s.attempts < :maxAttempts AND
            ((s.status = 'PENDING' AND s.nextAttemptAt <= :now) OR
             (s.status = 'RUNNING' AND s.leaseUntil <= :now)) ORDER BY s.nextAttemptAt, s.versionId
            """)
    List<KnowledgeDocumentSummary> findReady(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts, Pageable page);

    @Transactional
    @Modifying
    @Query("""
            UPDATE KnowledgeDocumentSummary s SET s.status = 'RUNNING', s.attempts = s.attempts + 1,
            s.claimToken = :token, s.leaseUntil = :lease WHERE s.versionId = :id AND s.attempts < :maxAttempts AND
            ((s.status = 'PENDING' AND s.nextAttemptAt <= :now) OR (s.status = 'RUNNING' AND s.leaseUntil <= :now))
            """)
    int claim(@Param("id") Long id, @Param("token") String token, @Param("now") Instant now,
              @Param("lease") Instant lease, @Param("maxAttempts") int maxAttempts);

    @Transactional
    @Modifying
    @Query("""
            UPDATE KnowledgeDocumentSummary s SET s.status = 'COMPLETED', s.summary = :summary, s.keywords = :keywords,
            s.searchTokens = :tokens, s.generatedAt = :now, s.leaseUntil = null, s.errorCode = null
            WHERE s.versionId = :id AND s.status = 'RUNNING' AND s.claimToken = :token
            """)
    int complete(@Param("id") Long id, @Param("token") String token, @Param("summary") String summary,
                 @Param("keywords") String keywords, @Param("tokens") String searchTokens, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("""
            UPDATE KnowledgeDocumentSummary s SET s.status = :status, s.nextAttemptAt = :next,
            s.leaseUntil = null, s.errorCode = :error WHERE s.versionId = :id AND s.status = 'RUNNING' AND s.claimToken = :token
            """)
    int fail(@Param("id") Long id, @Param("token") String token, @Param("status") String status,
             @Param("next") Instant next, @Param("error") String error);

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO knowledge_document_summary (version_id, status, next_attempt_at)
            SELECT t.version_id, 'PENDING', :now FROM knowledge_document_text t
            JOIN knowledge_document d ON d.published_version_id = t.version_id AND d.status = 'ACTIVE'
            WHERE t.status = 'COMPLETED'
              AND NOT EXISTS (SELECT 1 FROM knowledge_document_summary s WHERE s.version_id = t.version_id)
            ORDER BY t.version_id LIMIT :limit
            ON CONFLICT (version_id) DO NOTHING
            """, nativeQuery = true)
    int enqueueMissing(@Param("now") Instant now, @Param("limit") int limit);

    @Transactional
    @Modifying
    @Query("""
            UPDATE KnowledgeDocumentSummary s SET s.status = 'FAILED', s.leaseUntil = null, s.errorCode = 'ATTEMPTS_EXHAUSTED'
            WHERE s.attempts >= :maxAttempts AND
            (s.status = 'PENDING' OR (s.status = 'RUNNING' AND s.leaseUntil <= :now))
            """)
    int expireExhausted(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts);
}
