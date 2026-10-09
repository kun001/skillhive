package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.domain.skill.SkillIntroduction;
import com.iflytek.skillhub.domain.skill.SkillIntroductionRepository;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SkillListIntroductionTest {
    private final SkillIntroductionRepository repository = mock(SkillIntroductionRepository.class);
    private final SkillIntroductionService service = new SkillIntroductionService(repository, null, Clock.systemUTC());

    @Test
    void batchesSelectedVersionsAndNeverReadsAnUnselectedOwnerPreview() {
        var completed = job(11L, "COMPLETED");
        var pending = job(12L, "PENDING");
        when(repository.findByVersionIdIn(List.of(11L, 12L, 13L))).thenReturn(List.of(completed, pending));
        var first = summary(1L, 11L);
        var result = service.enrichSummaries(List.of(first, summary(2L, 12L), summary(3L, 11L), summary(4L, 13L)));
        assertThat(result).extracting(SkillSummaryResponse::id).containsExactly(1L, 2L, 3L, 4L);
        assertThat(result.getFirst().functionDescription().zh()).isEqualTo("功能描述");
        assertThat(result.getFirst().functionDescription().en()).isEqualTo("Function description");
        assertThat(result.getFirst().summary()).isEqualTo("Source description");
        assertThat(result.getFirst().withLabels(List.of()).functionDescription()).isEqualTo(result.getFirst().functionDescription());
        assertThat(result.get(1).functionDescription()).isNull();
        assertThat(result.get(2).functionDescription()).isEqualTo(result.getFirst().functionDescription());
        assertThat(result.get(3).functionDescription()).isNull();
        verify(repository).findByVersionIdIn(List.of(11L, 12L, 13L));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void emptyListsAndSkillsWithoutASelectedVersionDoNotReadJobs() {
        assertThat(service.enrichSummaries(List.of())).isEmpty();
        assertThat(service.enrichSummaries(List.of(summary(1L, null))).getFirst().functionDescription()).isNull();
        verifyNoInteractions(repository);
    }

    private SkillIntroduction job(Long id, String status) {
        var job = new SkillIntroduction(id, Instant.EPOCH);
        ReflectionTestUtils.setField(job, "status", status);
        ReflectionTestUtils.setField(job, "zhFunctionDescription", "功能描述");
        ReflectionTestUtils.setField(job, "enFunctionDescription", "Function description");
        return job;
    }

    private SkillSummaryResponse summary(Long skillId, Long versionId) {
        var selected = versionId == null ? null : new SkillLifecycleVersionResponse(versionId, "1.0.0", "PUBLISHED");
        return new SkillSummaryResponse(skillId, "skill-" + skillId, "Skill", "Source description", "PUBLIC", "ACTIVE",
                0L, 0, "global", Instant.EPOCH, false, selected, selected,
                new SkillLifecycleVersionResponse(99L, "2.0.0", "PENDING_REVIEW"), "PUBLISHED", null);
    }
}
