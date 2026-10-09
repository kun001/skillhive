package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.event.SkillVersionUploadedEvent;
import com.iflytek.skillhub.domain.skill.SkillIntroduction;
import com.iflytek.skillhub.domain.skill.SkillIntroductionRepository;
import com.iflytek.skillhub.dto.SkillIntroductionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.dto.SkillFunctionDescriptionResponse;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import com.iflytek.skillhub.infra.llm.SkillIntroductionProperties;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class SkillIntroductionService {
    private final SkillIntroductionRepository repository;
    private final SkillIntroductionProperties properties;
    private final Clock clock;

    public SkillIntroductionService(SkillIntroductionRepository repository, SkillIntroductionProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    // Queue persistence participates in the upload transaction. No model call runs here.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onUploaded(SkillVersionUploadedEvent event) {
        if (properties.enabled() && repository.findById(event.versionId()).isEmpty()) {
            repository.save(new SkillIntroduction(event.versionId(), clock.instant()));
        }
    }

    /** Enrich only the version already selected by the list's access/lifecycle projection. */
    public List<SkillSummaryResponse> enrichSummaries(
            List<SkillSummaryResponse> items) {
        var versionIds = items.stream().map(this::summaryVersionId)
                .filter(Objects::nonNull).distinct().toList();
        if (versionIds.isEmpty()) {
            return items;
        }
        var descriptions = repository.findByVersionIdIn(versionIds).stream()
                .filter(job -> "COMPLETED".equals(job.getStatus()))
                .filter(job -> job.getZhFunctionDescription() != null && !job.getZhFunctionDescription().isBlank()
                        && job.getEnFunctionDescription() != null && !job.getEnFunctionDescription().isBlank())
                .collect(Collectors.toMap(SkillIntroduction::getVersionId,
                        job -> new SkillFunctionDescriptionResponse(
                                job.getZhFunctionDescription(), job.getEnFunctionDescription())));
        return items.stream().map(item -> {
            var description = descriptions.get(summaryVersionId(item));
            return description == null ? item : item.withFunctionDescription(description);
        }).toList();
    }

    private Long summaryVersionId(SkillSummaryResponse item) {
        var version = item.headlineVersion() != null ? item.headlineVersion() : item.publishedVersion();
        return version == null ? null : version.id();
    }

    /** Called only after the normal version visibility checks have succeeded. */
    public SkillIntroductionResponse findByVersion(Long versionId) {
        return repository.findById(versionId).map(SkillIntroductionResponse::from).orElse(null);
    }
}
