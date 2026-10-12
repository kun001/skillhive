package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.event.NamespaceDeletedEvent;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationRepository;
import com.iflytek.skillhub.domain.skill.SkillStorageDeletionCompensationStatus;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test")
class NamespaceDeletedListenerIntegrationTest {
    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private SkillStorageDeletionCompensationRepository cleanup;
    @MockBean private ObjectStorageService storage;

    @Test
    void cleanupIsDurableOnlyAfterCommitAndRollbackNeverDeletesObjects() {
        String committed = "committed-" + UUID.randomUUID();
        String rolledBack = "rollback-" + UUID.randomUUID();
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> events.publishEvent(new NamespaceDeletedEvent(committed, List.of("file", "image"))));
        tx.executeWithoutResult(status -> {
            events.publishEvent(new NamespaceDeletedEvent(rolledBack, List.of("keep-this-file")));
            status.setRollbackOnly();
        });
        var queued = cleanup.findTop100ByStatusOrderByCreatedAtAsc(SkillStorageDeletionCompensationStatus.PENDING);
        assertThat(queued).filteredOn(record -> record.getNamespace().equals(committed))
                .singleElement().satisfies(record -> assertThat(record.getStorageKeysJson()).contains("file", "image"));
        assertThat(queued).noneMatch(record -> record.getNamespace().equals(rolledBack));
        verifyNoInteractions(storage);
    }
}
