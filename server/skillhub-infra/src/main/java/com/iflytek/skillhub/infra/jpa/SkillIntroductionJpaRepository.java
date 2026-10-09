package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillIntroduction;
import com.iflytek.skillhub.domain.skill.SkillIntroductionRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Atomic leases allow multiple application instances to share the durable queue. */
public interface SkillIntroductionJpaRepository extends JpaRepository<SkillIntroduction, Long>, SkillIntroductionRepository {
    @Query("""
            SELECT j FROM SkillIntroduction j WHERE j.attempts < :maxAttempts AND
            ((j.status = 'PENDING' AND j.nextAttemptAt <= :now) OR
             (j.status = 'RUNNING' AND j.leaseUntil <= :now)) ORDER BY j.nextAttemptAt, j.versionId
            """)
    List<SkillIntroduction> findReady(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts, Pageable page);

    @Transactional
    @Modifying
    @Query("""
            UPDATE SkillIntroduction j SET j.status = 'RUNNING', j.attempts = j.attempts + 1,
            j.claimToken = :token, j.leaseUntil = :lease WHERE j.versionId = :id AND j.attempts < :maxAttempts AND
            ((j.status = 'PENDING' AND j.nextAttemptAt <= :now) OR (j.status = 'RUNNING' AND j.leaseUntil <= :now))
            """)
    int claim(@Param("id") Long id, @Param("token") String token, @Param("now") Instant now,
              @Param("lease") Instant lease, @Param("maxAttempts") int maxAttempts);

    @Transactional
    @Modifying
    @Query("""
            UPDATE SkillIntroduction j SET j.status = 'COMPLETED', j.zhFunctionDescription = :zhFunction,
            j.zhUsageInstructions = :zhUsage, j.enFunctionDescription = :enFunction,
            j.enUsageInstructions = :enUsage, j.generatedAt = :now, j.leaseUntil = null, j.errorCode = null
            WHERE j.versionId = :id AND j.status = 'RUNNING' AND j.claimToken = :token
            """)
    int complete(@Param("id") Long id, @Param("token") String token, @Param("zhFunction") String zhFunction,
                 @Param("zhUsage") String zhUsage, @Param("enFunction") String enFunction,
                 @Param("enUsage") String enUsage, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("""
            UPDATE SkillIntroduction j SET j.status = :status, j.nextAttemptAt = :next,
            j.leaseUntil = null, j.errorCode = :error WHERE j.versionId = :id AND j.status = 'RUNNING' AND j.claimToken = :token
            """)
    int fail(@Param("id") Long id, @Param("token") String token, @Param("status") String status,
             @Param("next") Instant next, @Param("error") String error);

    @Transactional
    @Modifying
    @Query("""
            UPDATE SkillIntroduction j SET j.status = 'FAILED', j.leaseUntil = null, j.errorCode = 'ATTEMPTS_EXHAUSTED'
            WHERE j.attempts >= :maxAttempts AND
            (j.status = 'PENDING' OR (j.status = 'RUNNING' AND j.leaseUntil <= :now))
            """)
    int expireExhausted(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts);
}
