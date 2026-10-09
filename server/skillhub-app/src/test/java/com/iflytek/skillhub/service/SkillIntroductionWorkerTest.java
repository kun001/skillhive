package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.infra.llm.SkillIntroductionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SkillIntroductionWorkerTest {
    private final SkillIntroductionRepository jobs = mock(SkillIntroductionRepository.class);
    private final SkillVersionRepository versions = mock(SkillVersionRepository.class);
    private final SkillIntroductionGenerator generator = mock(SkillIntroductionGenerator.class);
    private final Instant now = Instant.parse("2026-10-09T00:00:00Z");
    private SkillIntroductionWorker worker;

    @BeforeEach
    void setup() {
        var config = new SkillIntroductionProperties(true, "http://localhost/v1", "", "model", Duration.ofSeconds(1),
                Duration.ofSeconds(5), 0.2, 2048, 16000, 3, Duration.ofSeconds(30), "none");
        worker = new SkillIntroductionWorker(jobs, versions, generator, config, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private SkillIntroduction job(int attempts) {
        var job = new SkillIntroduction(10L, now);
        ReflectionTestUtils.setField(job, "attempts", attempts);
        when(jobs.claim(eq(10L), anyString(), eq(now), any(), eq(3))).thenReturn(1);
        var version = new SkillVersion(1L, "1.0.0", "user");
        version.setParsedMetadataJson("{\"name\":\"demo\",\"description\":\"summary\",\"body\":\"body\",\"privateAudit\":\"excluded\"}");
        when(versions.findById(10L)).thenReturn(Optional.of(version));
        return job;
    }

    @Test
    void savesOnlyTheClaimedVersionAndExplicitSourceFields() throws Exception {
        var job = job(0);
        when(generator.generate(anyString())).thenReturn(new SkillIntroductionContent(
                new SkillIntroductionText("功能", "使用"), new SkillIntroductionText("Function", "Usage")));
        worker.process(job);
        verify(generator).generate("{\"name\":\"demo\",\"description\":\"summary\",\"body\":\"body\"}");
        verify(jobs).complete(eq(10L), anyString(), eq("功能"), eq("使用"), eq("Function"), eq("Usage"), eq(now));
    }

    @Test
    void retriesProviderFailuresWithoutAlteringUploadOrLifecycle() throws Exception {
        var job = job(0);
        when(generator.generate(anyString())).thenThrow(new java.net.http.HttpTimeoutException("private error"));
        worker.process(job);
        verify(jobs).fail(eq(10L), anyString(), eq("PENDING"), eq(now.plusSeconds(30)), eq("GENERATION_FAILED"));
        verify(versions, never()).save(any());
    }

    @Test
    void stopsAfterConfiguredAttemptsForInvalidResponses() throws Exception {
        var job = job(2);
        when(generator.generate(anyString())).thenThrow(new IllegalArgumentException("invalid"));
        worker.process(job);
        verify(jobs).fail(eq(10L), anyString(), eq("FAILED"), eq(now.plusSeconds(90)), eq("INVALID_OUTPUT"));
    }

    @Test
    void skipsJobsClaimedByAnotherInstance() {
        worker.process(new SkillIntroduction(10L, now));
        verifyNoInteractions(generator, versions);
    }
}
