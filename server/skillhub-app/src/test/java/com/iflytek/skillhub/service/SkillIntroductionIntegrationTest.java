package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.event.SkillVersionUploadedEvent;
import com.iflytek.skillhub.domain.skill.SkillIntroduction;
import com.iflytek.skillhub.domain.skill.SkillIntroductionGenerator;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillIntroductionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {"skillhub.introduction.enabled=true", "skillhub.introduction.base-url=http://localhost/v1",
        "skillhub.introduction.model=test", "skillhub.introduction.poll-interval-ms=3600000"})
@ActiveProfiles("test")
class SkillIntroductionIntegrationTest {
    @Autowired private SkillIntroductionRepository jobs;
    @Autowired private SkillVersionRepository versions;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private ApplicationEventPublisher events;
    @MockBean private SkillIntroductionGenerator generator;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("taskScheduler")
    private org.springframework.scheduling.TaskScheduler maintenanceScheduler;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("skillIntroductionScheduler")
    private org.springframework.scheduling.TaskScheduler introductionScheduler;

    @Test
    void queuesInUploadTransactionAndDoesNotCallModelOnUploadThread() {
        assertThat(introductionScheduler).isNotSameAs(maintenanceScheduler);
        Long id = new TransactionTemplate(transactions).execute(tx -> {
            var version = versions.save(new SkillVersion(12345L, UUID.randomUUID().toString(), "user"));
            events.publishEvent(new SkillVersionUploadedEvent(version.getId()));
            assertThat(jobs.findById(version.getId())).isEmpty();
            return version.getId();
        });
        assertThat(jobs.findById(id)).hasValueSatisfying(job -> assertThat(job.getStatus()).isEqualTo("PENDING"));
        verifyNoInteractions(generator);
        Long rolledBack = new TransactionTemplate(transactions).execute(tx -> {
            var version = versions.save(new SkillVersion(12345L, UUID.randomUUID().toString(), "user"));
            events.publishEvent(new SkillVersionUploadedEvent(version.getId()));
            tx.setRollbackOnly();
            return version.getId();
        });
        assertThat(jobs.findById(rolledBack)).isEmpty();
    }

    @Test
    void leasesPreventDuplicateClaimsAndStaleResultsAfterRestart() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        var version = versions.save(new SkillVersion(12345L, UUID.randomUUID().toString(), "user"));
        Long id = version.getId();
        jobs.save(new SkillIntroduction(id, now));
        assertThat(jobs.claim(id, "first", now, now.plusSeconds(30), 3)).isEqualTo(1);
        assertThat(jobs.claim(id, "second", now, now.plusSeconds(30), 3)).isZero();
        assertThat(jobs.claim(id, "second", now.plusSeconds(31), now.plusSeconds(60), 3)).isEqualTo(1);
        assertThat(jobs.complete(id, "first", "旧", "旧", "old", "old", now)).isZero();
        assertThat(jobs.complete(id, "second", "新", "新", "new", "new", now)).isEqualTo(1);
        assertThat(jobs.findById(id)).hasValueSatisfying(job -> {
            assertThat(job.getAttempts()).isEqualTo(2);
            assertThat(job.getStatus()).isEqualTo("COMPLETED");
            assertThat(job.getZhFunctionDescription()).isEqualTo("新");
        });
    }

    @Test
    void aCrashOnLastAttemptBecomesFailedAndVersionsKeepSeparateCopies() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        var first = versions.save(new SkillVersion(12345L, UUID.randomUUID().toString(), "user"));
        var second = versions.save(new SkillVersion(12345L, UUID.randomUUID().toString(), "user"));
        jobs.save(new SkillIntroduction(first.getId(), now));
        jobs.save(new SkillIntroduction(second.getId(), now));
        jobs.claim(first.getId(), "crashed", now, now.plusSeconds(30), 1);
        jobs.expireExhausted(now.plusSeconds(31), 1);
        assertThat(jobs.findById(first.getId())).hasValueSatisfying(job -> assertThat(job.getStatus()).isEqualTo("FAILED"));
        assertThat(jobs.findById(second.getId())).hasValueSatisfying(job -> assertThat(job.getStatus()).isEqualTo("PENDING"));
    }
}
