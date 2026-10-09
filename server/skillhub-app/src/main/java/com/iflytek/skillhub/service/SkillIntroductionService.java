package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.event.SkillVersionUploadedEvent;
import com.iflytek.skillhub.domain.skill.SkillIntroduction;
import com.iflytek.skillhub.domain.skill.SkillIntroductionRepository;
import com.iflytek.skillhub.dto.SkillIntroductionResponse;
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

    /** Called only after the normal version visibility checks have succeeded. */
    public SkillIntroductionResponse findByVersion(Long versionId) {
        return repository.findById(versionId).map(SkillIntroductionResponse::from).orElse(null);
    }
}
