package com.iflytek.skillhub.domain.skill;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface SkillIntroductionRepository {
    Optional<SkillIntroduction> findById(Long id);
    List<SkillIntroduction> findByVersionIdIn(java.util.Collection<Long> versionIds);
    SkillIntroduction save(SkillIntroduction job);
    List<SkillIntroduction> findReady(Instant now, int maxAttempts, Pageable page);
    int claim(Long id, String token, Instant now, Instant lease, int maxAttempts);
    int complete(Long id, String token, String zhFunction, String zhUsage, String enFunction, String enUsage, Instant now);
    int fail(Long id, String token, String status, Instant next, String error);
    int expireExhausted(Instant now, int maxAttempts);
}
