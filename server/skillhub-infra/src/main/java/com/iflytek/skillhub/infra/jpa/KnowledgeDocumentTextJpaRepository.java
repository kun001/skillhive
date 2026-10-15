package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentText;
import com.iflytek.skillhub.domain.knowledge.KnowledgeDocumentTextRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Atomic leases allow multiple application instances to share the durable extraction queue. */
public interface KnowledgeDocumentTextJpaRepository
        extends JpaRepository<KnowledgeDocumentText, Long>, KnowledgeDocumentTextRepository {

    List<KnowledgeDocumentText> findByVersionIdIn(Collection<Long> versionIds);

    @Query("""
            SELECT t FROM KnowledgeDocumentText t WHERE t.attempts < :maxAttempts AND
            ((t.status = 'PENDING' AND t.nextAttemptAt <= :now) OR
             (t.status = 'RUNNING' AND t.leaseUntil <= :now)) ORDER BY t.nextAttemptAt, t.versionId
            """)
    List<KnowledgeDocumentText> findReady(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts, Pageable page);

    @Transactional
    @Modifying
    @Query("""
            UPDATE KnowledgeDocumentText t SET t.status = 'RUNNING', t.attempts = t.attempts + 1,
            t.claimToken = :token, t.leaseUntil = :lease WHERE t.versionId = :id AND t.attempts < :maxAttempts AND
            ((t.status = 'PENDING' AND t.nextAttemptAt <= :now) OR (t.status = 'RUNNING' AND t.leaseUntil <= :now))
            """)
    int claim(@Param("id") Long id, @Param("token") String token, @Param("now") Instant now,
              @Param("lease") Instant lease, @Param("maxAttempts") int maxAttempts);

    @Transactional
    @Modifying
    @Query("""
            UPDATE KnowledgeDocumentText t SET t.status = 'COMPLETED', t.extractor = :extractor, t.content = :content,
            t.charCount = :charCount, t.truncated = :truncated, t.searchTokens = :tokens, t.extractedAt = :now,
            t.leaseUntil = null, t.errorCode = null
            WHERE t.versionId = :id AND t.status = 'RUNNING' AND t.claimToken = :token
            """)
    int complete(@Param("id") Long id, @Param("token") String token, @Param("extractor") String extractor,
                 @Param("content") String content, @Param("charCount") int charCount, @Param("truncated") boolean truncated,
                 @Param("tokens") String searchTokens, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("""
            UPDATE KnowledgeDocumentText t SET t.status = :status, t.extractor = :extractor, t.errorCode = :error,
            t.extractedAt = :now, t.leaseUntil = null
            WHERE t.versionId = :id AND t.status = 'RUNNING' AND t.claimToken = :token
            """)
    int finishWithoutText(@Param("id") Long id, @Param("token") String token, @Param("status") String status,
                          @Param("extractor") String extractor, @Param("error") String error, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("""
            UPDATE KnowledgeDocumentText t SET t.status = 'PENDING', t.nextAttemptAt = :next,
            t.leaseUntil = null, t.errorCode = :error WHERE t.versionId = :id AND t.status = 'RUNNING' AND t.claimToken = :token
            """)
    int fail(@Param("id") Long id, @Param("token") String token, @Param("next") Instant next, @Param("error") String error);

    @Transactional
    @Modifying
    @Query("""
            UPDATE KnowledgeDocumentText t SET t.status = 'FAILED', t.leaseUntil = null, t.errorCode = 'ATTEMPTS_EXHAUSTED'
            WHERE t.attempts >= :maxAttempts AND
            (t.status = 'PENDING' OR (t.status = 'RUNNING' AND t.leaseUntil <= :now))
            """)
    int expireExhausted(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts);
}
